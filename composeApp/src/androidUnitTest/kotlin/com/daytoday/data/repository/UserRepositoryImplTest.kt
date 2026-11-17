package com.daytoday.data.repository

import androidx.datastore.preferences.core.stringPreferencesKey
import com.daytoday.data.database.UserDao
import com.daytoday.data.database.UserEntity
import com.daytoday.model.User
import com.daytoday.network.AuthResponse
import com.daytoday.network.DayTodayApi
import com.daytoday.network.LoginRequest
import com.daytoday.repository.Result
import com.daytoday.testing.FakePreferencesDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryImplTest {

    private val dao: UserDao = mockk(relaxed = true)
    private val api: DayTodayApi = mockk()
    private val store = FakePreferencesDataStore()
    private val repo = UserRepositoryImpl(dao, api, store)
    private val tokenKey = stringPreferencesKey("auth_token")

    private val user = User("u1", "a@b.c", "Ada", 5L)

    private suspend fun storedToken() = store.data.first()[tokenKey]

    @Test
    fun `login caches the user and stores the token`() = runTest {
        coEvery { api.login(LoginRequest("a@b.c", "pw")) } returns AuthResponse(user, "jwt")
        val slot = slot<UserEntity>()
        coEvery { dao.insert(capture(slot)) } returns Unit

        val result = repo.login("a@b.c", "pw")

        assertEquals(user, (result as Result.Success).data)
        assertEquals("jwt", slot.captured.token)
        assertEquals("jwt", storedToken())
    }

    @Test
    fun `failed login returns the server message and stores nothing`() = runTest {
        coEvery { api.login(any()) } throws RuntimeException("Invalid credentials")
        val result = repo.login("a@b.c", "bad")
        assertEquals("Invalid credentials", (result as Result.Failure).error)
        assertEquals(null, storedToken())
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `getCurrentUser reads the cached user`() = runTest {
        coEvery { dao.getUser() } returns UserEntity.fromDomain(user, "jwt")
        assertEquals(user, (repo.getCurrentUser() as Result.Success).data)
    }

    @Test
    fun `getCurrentUser fails when nobody is logged in`() = runTest {
        coEvery { dao.getUser() } returns null
        assertTrue(repo.getCurrentUser() is Result.Failure)
    }

    @Test
    fun `logout clears the cache and the token`() = runTest {
        coEvery { api.login(any()) } returns AuthResponse(user, "jwt")
        repo.login("a@b.c", "pw")
        val result = repo.logout()
        assertTrue(result is Result.Success)
        coVerify { dao.clearAll() }
        assertEquals("", storedToken())
    }

    @Test
    fun `logout failure is reported`() = runTest {
        coEvery { dao.clearAll() } throws IllegalStateException("db locked")
        val result = repo.logout()
        assertEquals("db locked", (result as Result.Failure).error)
    }
}
