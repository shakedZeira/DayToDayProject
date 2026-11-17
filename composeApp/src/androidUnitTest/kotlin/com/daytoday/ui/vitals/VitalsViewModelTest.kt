package com.daytoday.ui.vitals

import com.daytoday.data.health.DayVitals
import com.daytoday.data.health.VitalsKind
import com.daytoday.data.health.VitalsLoad
import com.daytoday.data.health.VitalsRepository
import com.daytoday.data.health.VitalsSnapshot
import com.daytoday.data.health.VitalsUnavailable
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.ui.screen.vitals.VitalsViewModel
import com.daytoday.ui.screen.vitals.weightText
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VitalsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private class FakeRepo(var next: VitalsLoad) : VitalsRepository {
        var loads = 0
        override suspend fun load(days: Int): VitalsLoad {
            loads++
            return next
        }
    }

    private val snapshot = VitalsSnapshot(
        today = DayVitals(LocalDate.parse("2026-11-11")),
        history = emptyList(),
        latestWeightKg = 72.0,
        weightChangeKg = -0.5,
        grantedKinds = VitalsKind.values().toSet(),
    )

    @Test
    fun `loads on creation`() {
        val repo = FakeRepo(VitalsLoad.Ready(snapshot))
        val vm = VitalsViewModel(repo)
        assertFalse(vm.uiState.value.loading)
        assertEquals(VitalsLoad.Ready(snapshot), vm.uiState.value.result)
        assertEquals(1, repo.loads)
    }

    @Test
    fun `permission needed then granted after refresh`() {
        val repo = FakeRepo(VitalsLoad.NeedsPermission)
        val vm = VitalsViewModel(repo)
        assertEquals(VitalsLoad.NeedsPermission, vm.uiState.value.result)
        repo.next = VitalsLoad.Ready(snapshot)
        vm.refresh()
        assertTrue(vm.uiState.value.result is VitalsLoad.Ready)
    }

    @Test
    fun `unavailable is passed through`() {
        val vm = VitalsViewModel(FakeRepo(VitalsLoad.Unavailable(VitalsUnavailable.UNSUPPORTED)))
        assertEquals(VitalsLoad.Unavailable(VitalsUnavailable.UNSUPPORTED), vm.uiState.value.result)
    }

    @Test
    fun `failure can be retried`() {
        val repo = FakeRepo(VitalsLoad.Failed("boom"))
        val vm = VitalsViewModel(repo)
        repo.next = VitalsLoad.Ready(snapshot)
        vm.refresh()
        assertTrue(vm.uiState.value.result is VitalsLoad.Ready)
    }

    @Test
    fun `weight text shows the change only when given`() {
        val saved = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
        try {
            assertEquals("72.0 kg", weightText(72.0, null))
            assertEquals("72.0 kg (-0.5 in 7d)", weightText(72.0, -0.5))
            assertEquals("72.0 kg (+0.3 in 7d)", weightText(72.0, 0.3))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
