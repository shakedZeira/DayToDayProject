package com.daytoday.ui.settings

import android.app.Application
import android.content.Context
import com.daytoday.data.spotify.SpotifyPlaylistsResult
import com.daytoday.data.spotify.SpotifyRepository
import com.daytoday.repository.Result
import com.daytoday.testing.FakeServerUrlStore
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.testing.FakePreferencesDataStore
import com.daytoday.testing.newSettingsManager
import com.daytoday.ui.screen.settings.SettingsViewModel
import com.daytoday.usecase.UserUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val urls = FakeServerUrlStore()
    private val settings = newSettingsManager(FakePreferencesDataStore(), urls)
    private val userUseCases: UserUseCases = mockk(relaxed = true)
    private val spotify: SpotifyRepository = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private fun vm() = SettingsViewModel(userUseCases, settings, application, spotify, context)

    @Test
    fun `starts from stored preferences`() = runTest {
        settings.saveTheme("dark")
        settings.saveNotificationsEnabled(false)
        settings.updateWeightKg(82.5)
        val vm = vm()
        assertTrue(vm.isDarkMode.value)
        assertFalse(vm.pushEnabled.value)
        assertEquals(82.5, vm.userProfile.value.weightKg, 0.0)
    }

    @Test
    fun `setDarkMode persists and updates the flow`() = runTest {
        val vm = vm()
        vm.setDarkMode(true)
        assertEquals("dark", settings.theme.first())
        assertTrue(vm.isDarkMode.value)
        vm.setDarkMode(false)
        assertEquals("light", settings.theme.first())
        assertFalse(vm.isDarkMode.value)
    }

    @Test
    fun `profile edits flow back into the profile state`() = runTest {
        val vm = vm()
        vm.updateHeightCm(180.0)
        vm.updateAgeYears(41)
        vm.updateGender(false)
        val p = vm.userProfile.value
        assertEquals(180.0, p.heightCm, 0.0)
        assertEquals(41, p.ageYears)
        assertFalse(p.isMale)
    }

    @Test
    fun `invalid weight falls back to the default`() = runTest {
        val vm = vm()
        vm.updateWeightKg(-5.0)
        assertEquals(70.0, vm.userProfile.value.weightKg, 0.0)
    }

    @Test
    fun `invalid server url sets an error and valid one clears it`() = runTest {
        val vm = vm()
        vm.saveServerBaseUrl("ftp://nope")
        assertNotNull(vm.serverUrlError.value)
        assertNull(urls.saved)

        vm.saveServerBaseUrl(" https://api.example.com ")
        assertNull(vm.serverUrlError.value)
        assertEquals("https://api.example.com/", urls.saved)
    }

    @Test
    fun `clearServerUrlError resets the error`() = runTest {
        val vm = vm()
        vm.saveServerBaseUrl("bad")
        vm.clearServerUrlError()
        assertNull(vm.serverUrlError.value)
    }

    @Test
    fun `signOut logs out and clears the token`() = runTest {
        coEvery { userUseCases.logout() } returns Result.success(Unit)
        settings.saveAuthToken("tok")
        val vm = vm()
        vm.signOut()
        coVerify { userUseCases.logout() }
        assertEquals("", settings.authToken.first())
    }

    @Test
    fun `nba alerts toggle updates state`() = runTest {
        val vm = vm()
        vm.setNbaAlerts(true)
        assertTrue(vm.nbaAlerts.value)
        vm.setNbaAlerts(false)
        assertFalse(vm.nbaAlerts.value)
    }

    @Test
    fun `playlist load without login asks the user to log in`() = runTest {
        coEvery { spotify.myPlaylists() } returns SpotifyPlaylistsResult.NOT_LOGGED_IN
        val vm = vm()
        vm.loadSpotifyPlaylists()
        assertEquals("Log in to Spotify first", vm.spotifyMessage.value)
        assertFalse(vm.spotifyPlaylistsLoading.value)
    }

    @Test
    fun `playlist load error is shown`() = runTest {
        coEvery { spotify.myPlaylists() } returns SpotifyPlaylistsResult.ERROR("rate limited")
        val vm = vm()
        vm.loadSpotifyPlaylists()
        assertEquals("rate limited", vm.spotifyMessage.value)
    }

    @Test
    fun `empty playlist list explains itself`() = runTest {
        coEvery { spotify.myPlaylists() } returns SpotifyPlaylistsResult.SUCCESS(emptyList())
        val vm = vm()
        vm.loadSpotifyPlaylists()
        assertTrue(vm.spotifyMessage.value!!.startsWith("No playlists"))
    }

    @Test
    fun `selecting a playlist confirms and clears the picker`() = runTest {
        val vm = vm()
        vm.selectSpotifyPlaylist("spotify:playlist:1", "Gym")
        coVerify { spotify.selectPlaylist("spotify:playlist:1", "Gym") }
        assertEquals("Selected playlist: Gym", vm.spotifyMessage.value)
        assertTrue(vm.spotifyPlaylists.value.isEmpty())
    }

    @Test
    fun `spotify login message from the callback is surfaced then cleared`() = runTest {
        settings.setSpotifyLoginMessage("Connected")
        val vm = vm()
        assertEquals("Connected", vm.spotifyMessage.value)
        assertNull(settings.spotifyLoginMessage.first())
    }

    @Test
    fun `clearSpotifyMessage clears`() = runTest {
        val vm = vm()
        vm.selectSpotifyPlaylist("u", "n")
        vm.clearSpotifyMessage()
        assertNull(vm.spotifyMessage.value)
    }
}
