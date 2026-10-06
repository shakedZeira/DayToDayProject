package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "set_records",
    indices = [
        Index("sessionId"),
        Index("setNumber")
    ]
)
data class SetRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Double?,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?
) {
    fun toDomain(): SetRecord {
        return SetRecord(
            setNumber = setNumber,
            weight = weight,
            reps = reps,
            rpe = rpe,
            isCompleted = isCompleted,
            completedAt = completedAt
        )
    }

    companion object {
        fun fromDomain(record: SetRecord, sessionId: String): SetRecordEntity {
            return SetRecordEntity(
                sessionId = sessionId,
                setNumber = record.setNumber,
                weight = record.weight,
                reps = record.reps,
                rpe = record.rpe,
                isCompleted = record.isCompleted,
                completedAt = record.completedAt
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class SetRecord(
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Double?,
    val isCompleted: Boolean,
    val completedAt: Long?
)