package com.daytoday.ui.cooking

import com.daytoday.network.DayTodayApi
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.testing.newSettingsManager
import com.daytoday.ui.recipe
import com.daytoday.ui.screen.cooking.CookingViewModel
import com.daytoday.ui.screen.cooking.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CookingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val api: DayTodayApi = mockk()
    private val settings = newSettingsManager()

    private suspend fun vm(): CookingViewModel {
        settings.saveAuthToken("tok")
        return CookingViewModel(api, settings)
    }

    @Test
    fun `init loads all recipes with the stored token`() = runTest {
        coEvery { api.getRecipes("tok", null, false) } returns listOf(recipe("a"), recipe("b"))
        val state = vm().uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf("a", "b"), (state as UiState.Success).data.recipes.map { it.id })
    }

    @Test
    fun `init failure surfaces an error message`() = runTest {
        coEvery { api.getRecipes(any(), any(), any()) } throws RuntimeException("boom")
        val state = vm().uiState.value
        assertEquals("boom", (state as UiState.Error).message)
    }

    @Test
    fun `refresh after failure recovers`() = runTest {
        coEvery { api.getRecipes(any(), any(), any()) } throws RuntimeException("boom")
        val vm = vm()
        coEvery { api.getRecipes("tok", null, false) } returns listOf(recipe("a"))
        vm.refresh()
        assertTrue(vm.uiState.value is UiState.Success)
    }

    @Test
    fun `toggleFavorite updates only the matching recipe`() = runTest {
        val a = recipe("a")
        val b = recipe("b")
        coEvery { api.getRecipes("tok", null, false) } returns listOf(a, b)
        coEvery { api.toggleFavorite("tok", "b") } returns true
        val vm = vm()
        vm.toggleFavorite(b)
        val recipes = (vm.uiState.value as UiState.Success).data.recipes
        assertFalse(recipes.first { it.id == "a" }.isFavorited)
        assertTrue(recipes.first { it.id == "b" }.isFavorited)
    }

    @Test
    fun `toggleFavorite failure reports an error`() = runTest {
        coEvery { api.getRecipes(any(), any(), any()) } returns listOf(recipe("a"))
        coEvery { api.toggleFavorite(any(), any()) } throws RuntimeException("offline")
        val vm = vm()
        vm.toggleFavorite(recipe("a"))
        assertEquals("offline", (vm.uiState.value as UiState.Error).message)
    }

    @Test
    fun `filterByMeal requests that meal and remembers the selection`() = runTest {
        coEvery { api.getRecipes("tok", null, false) } returns listOf(recipe("a"))
        coEvery { api.getRecipes("tok", "lunch", false) } returns listOf(recipe("l", meal = "lunch"))
        val vm = vm()
        vm.filterByMeal("lunch")
        val data = (vm.uiState.value as UiState.Success).data
        assertEquals("lunch", data.selectedMeal)
        assertEquals(listOf("l"), data.recipes.map { it.id })
        coVerify { api.getRecipes("tok", "lunch", false) }
    }

    @Test
    fun `showFavorites requests favorites only`() = runTest {
        coEvery { api.getRecipes("tok", null, false) } returns listOf(recipe("a"))
        coEvery { api.getRecipes("tok", null, true) } returns listOf(recipe("f", favorited = true))
        val vm = vm()
        vm.showFavorites()
        val data = (vm.uiState.value as UiState.Success).data
        assertTrue(data.showFavorites)
        assertNull(data.selectedMeal)
        assertEquals(listOf("f"), data.recipes.map { it.id })
    }
}
