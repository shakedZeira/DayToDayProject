package com.daytoday.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.daytoday.network.ServerUrlStore
import com.daytoday.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Synchronous in-memory DataStore so SettingsManager can be used for real in JVM tests. */
class FakePreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class FakeServerUrlStore(private val default: String = "http://default.test/") : ServerUrlStore {
    var saved: String? = null
    override fun persistedUrl(): String? = saved
    override fun defaultUrl(): String = default
    override fun save(url: String): String? {
        saved = url
        return url
    }
}

fun newSettingsManager(
    store: FakePreferencesDataStore = FakePreferencesDataStore(),
    urls: FakeServerUrlStore = FakeServerUrlStore(),
): SettingsManager = SettingsManager(store, urls)

/** Installs an eager test dispatcher as Dispatchers.Main so viewModelScope runs inline. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
