package com.daytoday.ui.cooking

import com.daytoday.network.DayTodayApi
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.testing.newSettingsManager
import com.daytoday.ui.recipe
import com.daytoday.ui.screen.cooking.RecipeDetailViewModel
import com.daytoday.ui.screen.cooking.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecipeDetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val api: DayTodayApi = mockk()
    private val settings = newSettingsManager()

    private suspend fun vm(): RecipeDetailViewModel {
        settings.saveAuthToken("tok")
        return RecipeDetailViewModel(api, settings)
    }

    @Test
    fun `load fetches the recipe`() = runTest {
        coEvery { api.getRecipe("tok", "r1") } returns recipe("r1")
        val vm = vm()
        vm.load("r1")
        assertEquals("r1", (vm.uiState.value as UiState.Success).data.id)
    }

    @Test
    fun `load failure shows an error`() = runTest {
        coEvery { api.getRecipe(any(), any()) } throws RuntimeException("nope")
        val vm = vm()
        vm.load("r1")
        assertEquals("nope", (vm.uiState.value as UiState.Error).message)
    }

    @Test
    fun `reloading the same recipe does not hit the network again`() = runTest {
        coEvery { api.getRecipe("tok", "r1") } returns recipe("r1")
        val vm = vm()
        vm.load("r1")
        vm.load("r1")
        coVerify(exactly = 1) { api.getRecipe("tok", "r1") }
    }

    @Test
    fun `refresh forces a reload`() = runTest {
        coEvery { api.getRecipe("tok", "r1") } returns recipe("r1")
        val vm = vm()
        vm.load("r1")
        vm.refresh()
        coVerify(exactly = 2) { api.getRecipe("tok", "r1") }
    }

    @Test
    fun `favoriting bumps the count and unfavoriting lowers it`() = runTest {
        coEvery { api.getRecipe("tok", "r1") } returns recipe("r1", favorited = false, favoriteCount = 3)
        val vm = vm()
        vm.load("r1")

        coEvery { api.toggleFavorite("tok", "r1") } returns true
        vm.toggleFavorite()
        var r = (vm.uiState.value as UiState.Success).data
        assertTrue(r.isFavorited)
        assertEquals(4, r.favoriteCount)

        coEvery { api.toggleFavorite("tok", "r1") } returns false
        vm.toggleFavorite()
        r = (vm.uiState.value as UiState.Success).data
        assertEquals(3, r.favoriteCount)
    }

    @Test
    fun `favorite count never goes below zero`() = runTest {
        coEvery { api.getRecipe("tok", "r1") } returns recipe("r1", favorited = true, favoriteCount = 0)
        coEvery { api.toggleFavorite("tok", "r1") } returns false
        val vm = vm()
        vm.load("r1")
        vm.toggleFavorite()
        assertEquals(0, (vm.uiState.value as UiState.Success).data.favoriteCount)
    }

    @Test
    fun `favorite failure keeps the recipe on screen`() = runTest {
        coEvery { api.getRecipe("tok", "r1") } returns recipe("r1")
        coEvery { api.toggleFavorite(any(), any()) } throws RuntimeException("offline")
        val vm = vm()
        vm.load("r1")
        vm.toggleFavorite()
        assertTrue(vm.uiState.value is UiState.Success)
    }
}
