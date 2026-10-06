package com.daytoday.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.daytoday.data.database.UserDao
import com.daytoday.data.database.UserEntity
import com.daytoday.model.User
import com.daytoday.network.DayTodayApi
import com.daytoday.network.LoginRequest
import com.daytoday.network.RegisterRequest
import com.daytoday.repository.UserRepository
import com.daytoday.repository.Result
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val api: DayTodayApi,
    private val dataStore: DataStore<Preferences>
) : UserRepository {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")

    override suspend fun getCurrentUser(): Result<User> = withContext(Dispatchers.IO) {
        val userEntity = userDao.getUser()
        if (userEntity != null) {
            Result.success(userEntity.toDomain())
        } else {
            Result.failure("No user logged in")
        }
    }

    override suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(email, password))
            userDao.insert(UserEntity.fromDomain(response.user, response.token))
            dataStore.edit { it[TOKEN_KEY] = response.token }
            Result.success(response.user)
        } catch (e: Exception) {
            Result.failure(e.message ?: "Login failed", e)
        }
    }

    override suspend fun register(email: String, password: String, name: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.register(RegisterRequest(email, password, name))
            userDao.insert(UserEntity.fromDomain(response.user, response.token))
            dataStore.edit { it[TOKEN_KEY] = response.token }
            Result.success(response.user)
        } catch (e: Exception) {
            Result.failure(e.message ?: "Registration failed", e)
        }
    }

    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.clearAll()
            dataStore.edit { it[TOKEN_KEY] = "" }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.message ?: "Logout failed", e)
        }
    }
}