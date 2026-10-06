package com.daytoday.model

data class UserProfile(
    val weightKg: Double = 70.0,
    val heightCm: Double = 175.0,
    val ageYears: Int = 30,
    val isMale: Boolean = true,
)