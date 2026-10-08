package com.daytoday.settings

import kotlinx.coroutines.flow.first

suspend fun recordItalianLessonCompleted(settingsManager: SettingsManager) {
    val anchor = italianWeekStart()
    val entries = settingsManager.goals.first().map { italianWeeklyReset(it, anchor) }
    val target = entries.firstOrNull { it.type == GoalType.ITALIAN_LESSONS }
    val updated = if (target != null) {
        entries.map {
            if (it.id == target.id) it.copy(progress = it.progress + 1.0) else it
        }
    } else {
        entries + GoalEntry(
            id = generateGoalId(GoalType.ITALIAN_LESSONS, entries.map { e -> e.id }),
            type = GoalType.ITALIAN_LESSONS,
            target = GoalType.ITALIAN_LESSONS.defaultValue,
            period = GoalPeriod.WEEK,
            progress = 1.0,
            lastResetWeekStart = anchor,
        )
    }
    settingsManager.setGoals(updated)
}
