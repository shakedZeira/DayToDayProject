package com.daytoday.model

/**
 * Collapses duplicate injury reports describing the same player on the same team.
 *
 * ESPN's injury feed (`/apis/site/v2/sports/basketball/nba/injuries`) is a *current
 * snapshot*, but the local Room cache persists rows keyed by ESPN's record `id`.
 * When ESPN re-issues a report (new record id, updated date/comment/status) the
 * previous row survives, so the same player shows up several times in the Injuries
 * list. Keeping only the most recent report per player+team yields the single row
 * the user expects, and is also safe against an upstream payload that contains
 * literal duplicates.
 *
 * The most recent report wins, compared by [Injury.startDate], then
 * [Injury.lastUpdated], then [Injury.id] so the result is deterministic.
 */
fun List<Injury>.dedupeInjuries(): List<Injury> {
    val byPlayerAndTeam = LinkedHashMap<String, Injury>()
    for (injury in this) {
        val key = injury.dedupeKey()
        val current = byPlayerAndTeam[key]
        if (current == null || injury.isMoreRecentThan(current)) {
            byPlayerAndTeam[key] = injury
        }
    }
    return byPlayerAndTeam.values.toList()
}

private fun Injury.dedupeKey(): String {
    val player = playerId.ifBlank { playerName.trim().lowercase() }
    // Fall back to the record id when the player is unknown so unrelated
    // anonymous records are never merged together.
    return if (player.isBlank()) "id:$id" else "$player|$teamId"
}

private fun Injury.isMoreRecentThan(other: Injury): Boolean {
    if (startDate != other.startDate) return startDate > other.startDate
    if (lastUpdated != other.lastUpdated) return lastUpdated > other.lastUpdated
    return id > other.id
}
