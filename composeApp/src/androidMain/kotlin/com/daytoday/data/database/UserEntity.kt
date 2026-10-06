package com.daytoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.daytoday.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val createdAt: Long,
    val token: String
) {
    companion object {
        fun fromDomain(user: User, token: String): UserEntity {
            return UserEntity(
                id = user.id,
                email = user.email,
                name = user.name,
                createdAt = user.createdAt,
                token = token
            )
        }
    }

    fun toDomain(): User {
        return User(
            id = id,
            email = email,
            name = name,
            createdAt = createdAt
        )
    }
}