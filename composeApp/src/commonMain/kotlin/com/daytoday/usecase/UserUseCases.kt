package com.daytoday.usecase

import com.daytoday.repository.UserRepository
import com.daytoday.network.ApiClient
import com.daytoday.model.User
import com.daytoday.repository.Result
import javax.inject.Inject

class UserUseCases @Inject constructor(
    private val repository: UserRepository,
    private val apiClient: ApiClient
) {
    suspend fun login(email: String, password: String): Result<User> = repository.login(email, password)

    suspend fun register(email: String, password: String, name: String): Result<User> = repository.register(email, password, name)

    suspend fun logout(): Result<Unit> = repository.logout()

    suspend fun getCurrentUser(): Result<User> = repository.getCurrentUser()
}