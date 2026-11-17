package com.daytoday.data.sync

import com.daytoday.testing.FakeOutboxDao
import com.daytoday.testing.FakeSyncNoteDao
import com.daytoday.testing.RecordingHandler
import com.daytoday.testing.ScriptedSender
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxProcessorTest {

    private val outbox = FakeOutboxDao()
    private val notes = FakeSyncNoteDao()
    private val sender = ScriptedSender()
    private val handler = RecordingHandler(SyncEntity.TASK)
    private var token = "tok"
    private val processor = OutboxProcessor(outbox, notes, sender, { token }, listOf(handler), clock = { 42L })
    private val queue = OutboxQueue(outbox, clock = { 1L })

    private suspend fun enqueue(method: String, path: String, body: String? = null, temp: String? = null) =
        queue.enqueue(SyncEntity.TASK, method, path, body, temp)

    @Test
    fun `empty queue is done`() = runTest {
        assertEquals(DrainResult(DrainStatus.DONE, 0, 0), processor.drain())
    }

    @Test
    fun `delivers requests in the order they were made with the bearer token`() = runTest {
        enqueue("POST", "api/tasks", "{\"title\":\"a\"}")
        enqueue("PATCH", "api/tasks/1", "{\"status\":\"DONE\"}")
        enqueue("DELETE", "api/tasks/2")

        val result = processor.drain()

        assertEquals(DrainResult(DrainStatus.DONE, 3, 0), result)
        assertEquals(listOf("POST", "PATCH", "DELETE"), sender.calls.map { it.method })
        assertTrue(sender.calls.all { it.token == "tok" })
        assertTrue(outbox.snapshot.isEmpty())
        assertEquals(3, handler.sent.size)
    }

    @Test
    fun `network failure keeps the queue and retries later without reordering`() = runTest {
        enqueue("POST", "api/tasks", "a")
        enqueue("POST", "api/tasks", "b")
        sender.script += SendOutcome.Network(IOException("offline"))

        val first = processor.drain()
        assertEquals(DrainStatus.RETRY_LATER, first.status)
        assertEquals(2, outbox.snapshot.size)
        assertEquals(1, outbox.snapshot.first().attempts)

        val second = processor.drain()
        assertEquals(DrainStatus.DONE, second.status)
        assertEquals(listOf("a", "a", "b"), sender.calls.map { it.body })
    }

    @Test
    fun `an exception thrown by the sender counts as a network failure`() = runTest {
        enqueue("POST", "api/tasks")
        sender.throwOnSend = IllegalStateException("socket closed")
        assertEquals(DrainStatus.RETRY_LATER, processor.drain().status)
        assertEquals(1, outbox.snapshot.size)
    }

    @Test
    fun `server errors 408 and 429 are retried not dropped`() = runTest {
        for (status in listOf(500, 502, 503, 408, 429)) {
            val o = FakeOutboxDao()
            val p = OutboxProcessor(o, notes, ScriptedSender(SendOutcome.Http(status, "x")), { "tok" }, listOf(handler))
            OutboxQueue(o).enqueue(SyncEntity.TASK, "POST", "api/tasks", "{}")
            assertEquals("status $status", DrainStatus.RETRY_LATER, p.drain().status)
            assertEquals(1, o.snapshot.size)
        }
        assertTrue(notes.snapshot.isEmpty())
    }

    @Test
    fun `401 pauses the queue and keeps every entry`() = runTest {
        enqueue("POST", "api/tasks", "a")
        enqueue("POST", "api/tasks", "b")
        sender.script += SendOutcome.Http(401, "Unauthorized")

        val result = processor.drain()

        assertEquals(DrainStatus.AUTH_PAUSED, result.status)
        assertTrue(processor.authPaused.value)
        assertEquals(2, outbox.snapshot.size)
        assertTrue(notes.snapshot.isEmpty())
    }

    @Test
    fun `while paused nothing is sent until the user signs in again`() = runTest {
        enqueue("POST", "api/tasks", "a")
        sender.script += SendOutcome.Http(401, "Unauthorized")
        processor.drain()
        sender.calls.clear()

        assertEquals(DrainStatus.AUTH_PAUSED, processor.drain().status)
        assertTrue(sender.calls.isEmpty())

        token = "fresh"
        processor.resumeAfterLogin()
        assertFalse(processor.authPaused.value)
        assertEquals(DrainStatus.DONE, processor.drain().status)
        assertEquals("fresh", sender.calls.single().token)
        assertTrue(outbox.snapshot.isEmpty())
    }

    @Test
    fun `a blank token pauses without calling the server`() = runTest {
        enqueue("POST", "api/tasks")
        token = ""
        assertEquals(DrainStatus.AUTH_PAUSED, processor.drain().status)
        assertTrue(sender.calls.isEmpty())
        assertEquals(1, outbox.snapshot.size)
    }

    @Test
    fun `poison 4xx is dropped with a visible note and the rest continues`() = runTest {
        enqueue("PATCH", "api/tasks/1", "bad")
        enqueue("PATCH", "api/tasks/2", "good")
        sender.script += SendOutcome.Http(400, "title required")

        val result = processor.drain()

        assertEquals(DrainResult(DrainStatus.DONE, 1, 1), result)
        assertTrue(outbox.snapshot.isEmpty())
        assertEquals(1, handler.dropped.size)
        assertEquals("api/tasks/1", handler.dropped.single().path)
        val note = notes.snapshot.single()
        assertTrue(note.message.contains("400"))
        assertTrue(note.message.contains("title required"))
        assertEquals(42L, note.createdAt)
    }

    @Test
    fun `404 on a DELETE means already gone and is not an error`() = runTest {
        enqueue("DELETE", "api/tasks/9")
        sender.script += SendOutcome.Http(404, "not found")
        val result = processor.drain()
        assertEquals(DrainResult(DrainStatus.DONE, 1, 0), result)
        assertTrue(notes.snapshot.isEmpty())
    }

    @Test
    fun `404 on a PATCH is poison`() = runTest {
        enqueue("PATCH", "api/tasks/9", "{}")
        sender.script += SendOutcome.Http(404, "not found")
        assertEquals(1, processor.drain().dropped)
        assertEquals(1, notes.snapshot.size)
    }

    @Test
    fun `create assigns a server id and later requests are rewritten to it`() = runTest {
        handler.createdId = "srv-1"
        enqueue("POST", "api/tasks", "{\"title\":\"t\"}", temp = "local-abc")
        enqueue("PATCH", "api/tasks/local-abc", "{\"status\":\"DONE\"}")
        enqueue("DELETE", "api/tasks/local-abc")
        sender.script += SendOutcome.Ok("{\"id\":\"srv-1\"}")

        processor.drain()

        assertEquals(listOf("api/tasks", "api/tasks/srv-1", "api/tasks/srv-1"), sender.calls.map { it.path })
        assertEquals(listOf("local-abc" to "srv-1"), handler.assigned)
    }

    @Test
    fun `temp id is also rewritten inside bodies`() = runTest {
        handler.createdId = "srv-1"
        enqueue("POST", "api/goals", "{}", temp = "local-x")
        enqueue("POST", "api/other", "{\"ref\":\"local-x\"}")
        sender.script += SendOutcome.Ok(null)
        processor.drain()
        assertEquals("{\"ref\":\"srv-1\"}", sender.calls[1].body)
    }

    @Test
    fun `a rejected create drops the follow-ups that depended on it`() = runTest {
        enqueue("POST", "api/tasks", "{}", temp = "local-abc")
        enqueue("PATCH", "api/tasks/local-abc", "{}")
        enqueue("PATCH", "api/tasks/other", "{}")
        sender.script += SendOutcome.Http(422, "invalid")

        val result = processor.drain()

        assertEquals(2, result.dropped)
        assertEquals(1, result.sent)
        assertEquals("api/tasks/other", sender.calls.last().path)
        assertEquals(2, handler.dropped.size)
        assertEquals(1, notes.snapshot.size)
    }

    @Test
    fun `entries of other entity types go to their own handler`() = runTest {
        val mealHandler = RecordingHandler(SyncEntity.MEAL)
        val p = OutboxProcessor(outbox, notes, sender, { "tok" }, listOf(handler, mealHandler))
        queue.enqueue(SyncEntity.MEAL, "POST", "api/foods/meals", "{}", null)
        p.drain()
        assertEquals(1, mealHandler.sent.size)
        assertTrue(handler.sent.isEmpty())
    }
}
