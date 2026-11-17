package com.daytoday.data.sync

import com.daytoday.network.DayTodayApi
import com.daytoday.network.TaskDto
import com.daytoday.testing.CountingTrigger
import com.daytoday.testing.FakeOutboxDao
import com.daytoday.testing.FakeSyncNoteDao
import com.daytoday.testing.FakeTaskDao
import com.daytoday.testing.ScriptedSender
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksRepositoryTest {

    private val tasks = FakeTaskDao()
    private val outbox = FakeOutboxDao()
    private val notes = FakeSyncNoteDao()
    private val api: DayTodayApi = mockk()
    private val trigger = CountingTrigger()
    private var now = 1_000L
    private var ids = 0
    private val repo = TasksRepository(
        tasks, outbox, api, { "tok" }, trigger,
        clock = { now++ }, newId = { "local-${++ids}" },
    )
    private val sender = ScriptedSender()
    private val processor = OutboxProcessor(outbox, notes, sender, { "tok" }, listOf(repo))

    @Test
    fun `add shows the task immediately and queues a create`() = runTest {
        val id = repo.add("  Buy milk ", dueAt = "2026-11-18T00:00:00Z")

        assertEquals("local-1", id)
        val item = repo.items.first().single()
        assertEquals("Buy milk", item.title)
        assertFalse(item.done)
        assertTrue(item.unsynced)

        val entry = outbox.snapshot.single()
        assertEquals("POST", entry.method)
        assertEquals("api/tasks", entry.path)
        assertEquals("local-1", entry.tempId)
        assertTrue(entry.body!!.contains("\"title\":\"Buy milk\""))
        assertTrue(entry.body!!.contains("2026-11-18"))
        assertEquals(1, trigger.requests)
    }

    @Test
    fun `blank titles are ignored`() = runTest {
        assertNull(repo.add("   "))
        assertTrue(outbox.snapshot.isEmpty())
        assertEquals(0, trigger.requests)
    }

    @Test
    fun `setDone is optimistic and queues a patch`() = runTest {
        tasks.upsert(TaskEntity("t1", "x", null, "PENDING", null, "none", null, null, 1))
        repo.setDone("t1", true)
        assertTrue(repo.items.first().single().done)
        val entry = outbox.snapshot.single()
        assertEquals("PATCH", entry.method)
        assertEquals("api/tasks/t1", entry.path)
        assertEquals("{\"status\":\"DONE\"}", entry.body)
        repo.setDone("t1", false)
        assertFalse(repo.items.first().single().done)
    }

    @Test
    fun `setDone on an unknown task does nothing`() = runTest {
        repo.setDone("nope", true)
        assertTrue(outbox.snapshot.isEmpty())
    }

    @Test
    fun `rename updates locally and queues a patch`() = runTest {
        tasks.upsert(TaskEntity("t1", "x", null, "PENDING", null, "none", null, null, 1))
        repo.rename("t1", " y ")
        assertEquals("y", repo.items.first().single().title)
        assertEquals("{\"title\":\"y\"}", outbox.snapshot.single().body)
        repo.rename("t1", "  ")
        assertEquals(1, outbox.snapshot.size)
    }

    @Test
    fun `deleting a synced task queues a DELETE`() = runTest {
        tasks.upsert(TaskEntity("t1", "x", null, "PENDING", null, "none", null, null, 1))
        repo.delete("t1")
        assertTrue(repo.items.first().isEmpty())
        val entry = outbox.snapshot.single()
        assertEquals("DELETE", entry.method)
        assertEquals("api/tasks/t1", entry.path)
    }

    @Test
    fun `deleting a task that never synced cancels its queued writes`() = runTest {
        val id = repo.add("temp")!!
        repo.setDone(id, true)
        repo.delete(id)
        assertTrue(outbox.snapshot.isEmpty())
        assertTrue(repo.items.first().isEmpty())
    }

    @Test
    fun `refresh is skipped while writes are pending so local edits are not clobbered`() = runTest {
        repo.add("local")
        coEvery { api.getTasks(any()) } returns listOf(TaskDto("s1", "server"))
        assertFalse(repo.refresh())
        assertEquals(listOf("local"), repo.items.first().map { it.title })
    }

    @Test
    fun `refresh replaces local rows with the server list in server order`() = runTest {
        tasks.upsert(TaskEntity("old", "old", null, "PENDING", null, "none", null, null, 1))
        coEvery { api.getTasks("tok") } returns listOf(
            TaskDto("s1", "first", status = "DONE"),
            TaskDto("s2", "second"),
        )
        assertTrue(repo.refresh())
        val items = repo.items.first()
        assertEquals(listOf("s1", "s2"), items.map { it.id })
        assertTrue(items[0].done)
        assertFalse(items[0].unsynced)
    }

    @Test
    fun `refresh failure keeps the cached tasks`() = runTest {
        tasks.upsert(TaskEntity("t1", "cached", null, "PENDING", null, "none", null, null, 1))
        coEvery { api.getTasks(any()) } throws IOException("offline")
        assertFalse(repo.refresh())
        assertEquals(listOf("cached"), repo.items.first().map { it.title })
    }

    @Test
    fun `offline create then edit syncs in order and swaps the temp id for the server id`() = runTest {
        val id = repo.add("Call mum")!!
        repo.setDone(id, true)
        sender.script += SendOutcome.Ok("{\"id\":\"srv-9\",\"title\":\"Call mum\"}")

        val result = processor.drain()

        assertEquals(DrainStatus.DONE, result.status)
        assertEquals(listOf("api/tasks", "api/tasks/srv-9"), sender.calls.map { it.path })
        val item = repo.items.first().single()
        assertEquals("srv-9", item.id)
        assertTrue(item.done)
        assertFalse(item.unsynced)
    }

    @Test
    fun `server rejecting a create removes the optimistic row and leaves a note`() = runTest {
        repo.add("bad")
        sender.script += SendOutcome.Http(400, "title required")
        processor.drain()
        assertTrue(repo.items.first().isEmpty())
        assertEquals(1, notes.snapshot.size)
    }

    @Test
    fun `changes survive being offline and are delivered later`() = runTest {
        repo.add("a")
        sender.script += SendOutcome.Network(IOException("offline"))
        assertEquals(DrainStatus.RETRY_LATER, processor.drain().status)
        assertEquals(1, repo.items.first().size)
        assertEquals(1, outbox.snapshot.size)
        assertEquals(DrainStatus.DONE, processor.drain().status)
        assertTrue(outbox.snapshot.isEmpty())
    }
}
