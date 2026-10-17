package com.daytoday.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InjuryDedupeTest {

    private fun injury(
        id: String,
        playerId: String = "p-$id",
        playerName: String = "Player $id",
        teamId: String = "1",
        teamName: String = "Atlanta Hawks",
        status: String = "Out",
        description: String = "Knee",
        startDate: Long = 1_000L,
        lastUpdated: Long = 2_000L,
    ) = Injury(
        id = id,
        playerId = playerId,
        playerName = playerName,
        teamId = teamId,
        teamName = teamName,
        status = status,
        description = description,
        startDate = startDate,
        endDate = null,
        lastUpdated = lastUpdated,
    )

    @Test
    fun `identical duplicates collapse to a single row`() {
        val duplicate = injury(id = "1", startDate = 500L)
        val result = listOf(duplicate, duplicate.copy(), duplicate.copy()).dedupeInjuries()
        assertEquals(listOf(duplicate), result)
    }

    @Test
    fun `same player re-reported under a new ESPN record id keeps the newest report`() {
        // ESPN re-issues the record: new id, newer date, updated status.
        val stale = injury(id = "100", playerId = "42", status = "Day-To-Day", startDate = 100L)
        val fresh = injury(id = "900", playerId = "42", status = "Out", startDate = 900L)

        val result = listOf(stale, fresh).dedupeInjuries()

        assertEquals(listOf(fresh), result)
    }

    @Test
    fun `most recent report wins regardless of input order`() {
        val older = injury(id = "1", playerId = "7", startDate = 100L)
        val newer = injury(id = "2", playerId = "7", startDate = 900L)

        assertEquals(listOf(newer), listOf(newer, older).dedupeInjuries())
        assertEquals(listOf(newer), listOf(older, newer).dedupeInjuries())
    }

    @Test
    fun `same player on different teams is not merged`() {
        val a = injury(id = "1", playerId = "42", teamId = "1")
        val b = injury(id = "2", playerId = "42", teamId = "2", teamName = "Boston Celtics")

        assertEquals(listOf(a, b), listOf(a, b).dedupeInjuries())
    }

    @Test
    fun `distinct players on the same team are not merged`() {
        val a = injury(id = "1", playerId = "42", playerName = "Player A")
        val b = injury(id = "2", playerId = "43", playerName = "Player B")

        assertEquals(listOf(a, b), listOf(a, b).dedupeInjuries())
    }

    @Test
    fun `falls back to player name when player id is blank`() {
        val stale = injury(id = "1", playerId = "", playerName = "  LeBron James ", startDate = 100L)
        val fresh = injury(id = "2", playerId = "", playerName = "LeBron James", startDate = 900L)

        assertEquals(listOf(fresh), listOf(stale, fresh).dedupeInjuries())
    }

    @Test
    fun `records without a player identity are never merged`() {
        val a = injury(id = "1", playerId = "", playerName = "")
        val b = injury(id = "2", playerId = "", playerName = "")

        assertEquals(listOf(a, b), listOf(a, b).dedupeInjuries())
    }

    @Test
    fun `keeps a single row per player for a realistic ESPN snapshot with repeats`() {
        // Simulates a cache poisoned by the old insert-only behaviour: the same
        // three players each appear once per historical record id.
        val dupes = listOf(
            injury(id = "1", playerId = "42", startDate = 100L),
            injury(id = "2", playerId = "42", startDate = 200L),
            injury(id = "3", playerId = "42", startDate = 300L),
            injury(id = "4", playerId = "43", startDate = 150L),
            injury(id = "5", playerId = "43", startDate = 250L),
            injury(id = "6", playerId = "44", startDate = 180L),
        )

        val result = dupes.dedupeInjuries()

        assertEquals(3, result.size)
        assertEquals(
            setOf("3", "5", "6"),
            result.map { it.id }.toSet(),
        )
        assertTrue(result.all { it.id in setOf("3", "5", "6") })
    }

    @Test
    fun `ties on startDate fall back to lastUpdated then id`() {
        val a = injury(id = "5", playerId = "42", startDate = 100L, lastUpdated = 10L)
        val b = injury(id = "9", playerId = "42", startDate = 100L, lastUpdated = 10L)

        assertEquals(listOf(b), listOf(a, b).dedupeInjuries())
        assertEquals(listOf(b), listOf(b, a).dedupeInjuries())
    }
}
