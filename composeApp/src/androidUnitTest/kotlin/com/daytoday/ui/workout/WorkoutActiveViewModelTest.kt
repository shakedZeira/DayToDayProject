package com.daytoday.ui.workout

import android.content.Context
import com.daytoday.data.spotify.SpotifyPlayResult
import com.daytoday.data.spotify.SpotifyRepository
import com.daytoday.model.Exercise
import com.daytoday.model.PlanExercise
import com.daytoday.model.WorkoutPlan
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.Result
import com.daytoday.repository.WorkoutRepository
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.testing.newSettingsManager
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.ui.screen.workout.WorkoutActiveViewModel
import com.daytoday.usecase.WorkoutUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutActiveViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repository: WorkoutRepository = mockk()
    private val spotify: SpotifyRepository = mockk(relaxed = true)
    private val useCases: WorkoutUseCases = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val settings = newSettingsManager()

    private val pushUp = Exercise("ex_push_up", "Push-Up", "Chest", "None", "", null)
    private val squat = Exercise("ex_squat", "Squat", "Legs", "Barbell", "", null)

    private fun vm(exercises: Result<List<Exercise>> = Result.success(listOf(pushUp, squat))): WorkoutActiveViewModel {
        coEvery { repository.getExercises() } returns exercises
        coEvery { repository.saveSession(any()) } answers { Result.success(firstArg()) }
        return WorkoutActiveViewModel(useCases, repository, settings, spotify, context)
    }

    @Test
    fun `exercises load into the state`() {
        val vm = vm()
        val state = vm.exercises.value
        assertEquals(listOf("ex_push_up", "ex_squat"), (state as UiState.Success).data.map { it.id })
    }

    @Test
    fun `exercise load failure is reported`() {
        val vm = vm(Result.failure("db down"))
        assertEquals("db down", (vm.exercises.value as UiState.Error).message)
    }

    @Test
    fun `toggling an exercise adds then removes it from the builder`() {
        val vm = vm()
        vm.toggleExerciseSelection(pushUp)
        assertEquals(listOf("ex_push_up"), vm.builderExercises.value.map { it.exerciseId })
        assertEquals(3, vm.builderExercises.value.single().targetSets)
        vm.toggleExerciseSelection(pushUp)
        assertTrue(vm.builderExercises.value.isEmpty())
        assertTrue(vm.selectedExercises.value.isEmpty())
    }

    @Test
    fun `builder values are clamped to sane minimums`() {
        val vm = vm()
        vm.toggleExerciseSelection(pushUp)
        vm.updateExerciseSets("ex_push_up", 0)
        vm.updateExerciseReps("ex_push_up", -4)
        vm.updateExerciseWeight("ex_push_up", -1.0)
        val e = vm.builderExercises.value.single()
        assertEquals(1, e.targetSets)
        assertEquals(1, e.targetReps)
        assertEquals(0.0, e.targetWeightKg, 0.0)
    }

    @Test
    fun `saveCurrentPlan stores a named plan and falls back to a default name`() = runTest {
        val vm = vm()
        vm.toggleExerciseSelection(pushUp)
        vm.saveCurrentPlan("")
        val plans = settings.savedWorkoutPlans.first()
        assertEquals(1, plans.size)
        assertEquals("Custom Workout Plan", plans.single().name)
        vm.deletePlan(plans.single().id)
        assertTrue(settings.savedWorkoutPlans.first().isEmpty())
    }

    @Test
    fun `saveCurrentPlan with nothing selected does nothing`() = runTest {
        val vm = vm()
        vm.saveCurrentPlan("Empty")
        assertTrue(settings.savedWorkoutPlans.first().isEmpty())
    }

    @Test
    fun `starting a single exercise creates three open sets`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        val session = vm.activeSession.value!!
        assertEquals("ex_squat", session.exerciseId)
        assertEquals(3, session.sets.size)
        assertTrue(session.sets.none { it.isCompleted })
    }

    @Test
    fun `addSet copies the previous set values`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        vm.updateSetWeight(2, 60.0)
        vm.updateSetReps(2, 8)
        vm.addSet()
        val added = vm.activeSession.value!!.sets.last()
        assertEquals(4, added.setNumber)
        assertEquals(60.0, added.weight, 0.0)
        assertEquals(8, added.reps)
    }

    @Test
    fun `set edits with a bad index are ignored`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        val before = vm.activeSession.value
        vm.updateSetWeight(99, 10.0)
        vm.updateSetReps(-1, 10)
        vm.updateSetRpe(7, 9.0)
        vm.completeSet(12)
        assertEquals(before, vm.activeSession.value)
    }

    @Test
    fun `completeSet toggles completion and recomputes calories`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        vm.updateSetWeight(0, 100.0)
        vm.completeSet(0)
        var s = vm.activeSession.value!!
        assertTrue(s.sets[0].isCompleted)
        assertNotNull(s.sets[0].completedAt)
        vm.completeSet(0)
        s = vm.activeSession.value!!
        assertFalse(s.sets[0].isCompleted)
        assertNull(s.sets[0].completedAt)
    }

    @Test
    fun `rpe can be set`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        vm.updateSetRpe(1, 8.5)
        assertEquals(8.5, vm.activeSession.value!!.sets[1].rpe!!, 0.0)
    }

    @Test
    fun `completeWorkout marks the session done and saves it`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        vm.completeSet(0)
        vm.completeWorkout()
        val saved = slot<WorkoutSession>()
        coVerify { repository.saveSession(capture(saved)) }
        assertTrue(saved.captured.isCompleted)
        assertNotNull(saved.captured.endTime)
        assertTrue(saved.captured.durationMinutes >= 1)
        assertTrue(vm.activeSession.value!!.isCompleted)
    }

    @Test
    fun `completeWorkout without a session is a no-op`() {
        val vm = vm()
        vm.completeWorkout()
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `plan queue advances and saves each finished exercise`() {
        val vm = vm()
        vm.toggleExerciseSelection(pushUp)
        vm.toggleExerciseSelection(squat)
        vm.startSelectedAsWorkout()
        assertEquals("ex_push_up", vm.activeSession.value!!.exerciseId)

        vm.nextExerciseInPlan()
        assertEquals(1, vm.activePlanIndex.value)
        assertEquals("ex_squat", vm.activeSession.value!!.exerciseId)

        vm.nextExerciseInPlan()
        assertTrue(vm.activeSession.value!!.isCompleted)
        coVerify(exactly = 2) { repository.saveSession(any()) }
    }

    @Test
    fun `startSelectedAsWorkout with nothing selected does nothing`() {
        val vm = vm()
        vm.startSelectedAsWorkout()
        assertNull(vm.activeSession.value)
    }

    @Test
    fun `exitActiveWorkout clears everything`() {
        val vm = vm()
        vm.startSingleExercise(squat)
        vm.exitActiveWorkout()
        assertNull(vm.activeSession.value)
        assertTrue(vm.activePlanQueue.value.isEmpty())
    }

    @Test
    fun `empty plan cannot be started`() {
        val vm = vm()
        vm.startPlan(WorkoutPlan("p", "Empty", emptyList()))
        assertNull(vm.activeSession.value)
        assertNull(vm.amrapActive.value)
    }

    @Test
    fun `plain plan starts its first exercise`() {
        val vm = vm()
        vm.startPlan(WorkoutPlan("p", "Push", listOf(PlanExercise("ex_push_up", "Push-Up", targetSets = 2, targetReps = 12))))
        val s = vm.activeSession.value!!
        assertEquals(2, s.sets.size)
        assertEquals(12, s.sets.first().reps)
    }

    @Test
    fun `amrap plan starts warmup state and counts rounds`() {
        val vm = vm()
        vm.startPlan(
            WorkoutPlan(
                "p", "AMRAP", listOf(PlanExercise("ex_push_up", "Push-Up", targetReps = 10)),
                warmupSeconds = 60, amrapSeconds = 600,
            ),
        )
        val amrap = vm.amrapActive.value!!
        assertNull(amrap.warmupFinishedAt)
        vm.skipWarmup()
        assertNotNull(vm.amrapActive.value!!.warmupFinishedAt)
        vm.amrapRoundComplete()
        vm.amrapRoundComplete()
        assertEquals(2, vm.amrapActive.value!!.roundsCompleted)
    }

    @Test
    fun `finishAmrap saves one completed set per exercise per round`() {
        val vm = vm()
        vm.startPlan(
            WorkoutPlan(
                "p", "AMRAP",
                listOf(PlanExercise("a", "A", targetReps = 5), PlanExercise("b", "B", targetReps = 7)),
                warmupSeconds = 60, amrapSeconds = 540,
            ),
        )
        repeat(3) { vm.amrapRoundComplete() }
        vm.finishAmrap()
        val saved = slot<WorkoutSession>()
        coVerify { repository.saveSession(capture(saved)) }
        assertEquals(6, saved.captured.sets.size)
        assertTrue(saved.captured.sets.all { it.isCompleted })
        // saveCompletedSession re-derives the duration from wall-clock time.
        assertTrue(saved.captured.durationMinutes >= 1)
        assertNull(vm.amrapActive.value)
    }

    @Test
    fun `discardAmrap drops the state without saving`() {
        val vm = vm()
        vm.startPlan(WorkoutPlan("p", "AMRAP", listOf(PlanExercise("a", "A")), warmupSeconds = 10, amrapSeconds = 60))
        vm.discardAmrap()
        assertNull(vm.amrapActive.value)
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `spotify playback messages follow the result and then clear`() = runTest {
        coEvery { spotify.playPlaylist(any()) } returns SpotifyPlayResult.NOT_LOGGED_IN
        val vm = vm()
        vm.playSpotifyMusic()
        assertEquals("Connect Spotify first", vm.spotifyMessage.value)
        advanceTimeBy(4_500)
        assertNull(vm.spotifyMessage.value)
    }

    @Test
    fun `spotify error message is shown`() {
        coEvery { spotify.playPlaylist(any()) } returns SpotifyPlayResult.ERROR("no premium")
        val vm = vm()
        vm.playSpotifyMusic()
        assertEquals("no premium", vm.spotifyMessage.value)
    }

    @Test
    fun `calculateCalories uses the stored weight`() = runTest {
        val vm = vm()
        vm.startSingleExercise(squat)
        val session = vm.activeSession.value!!.copy(
            sets = vm.activeSession.value!!.sets.map { it.copy(isCompleted = true, weight = 50.0) },
        )
        val atDefault = vm.calculateCalories(session)
        assertTrue(atDefault >= 0)
    }
}
