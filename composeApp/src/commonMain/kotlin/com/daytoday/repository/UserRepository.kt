package com.daytoday.repository

import com.daytoday.model.User

interface UserRepository {
    suspend fun getCurrentUser(): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String, name: String): Result<User>
    suspend fun logout(): Result<Unit>
}