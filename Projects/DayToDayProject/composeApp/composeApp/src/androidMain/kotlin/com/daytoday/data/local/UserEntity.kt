package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index("userId", unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    val email: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val token: String?
) {
    fun toDomain(): User {
        return User(
            id = userId,
            email = email,
            name = name,
            createdAt = createdAt,
            token = token
        )
    }

    companion object {
        fun fromDomain(user: User, token: String? = null): UserEntity {
            return UserEntity(
                userId = user.id,
                email = user.email,
                name = user.name,
                createdAt = user.createdAt,
                token = token ?: user.token
            )
        }
    }
}

data class User(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: Long,
    val token: String?
)