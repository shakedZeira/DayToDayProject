package com.daytoday.ui

import com.daytoday.network.RecipeView

fun recipe(
    id: String,
    meal: String = "dinner",
    favorited: Boolean = false,
    favoriteCount: Int = 0,
) = RecipeView(
    id = id,
    title = "Recipe $id",
    description = null,
    meal = meal,
    difficulty = "easy",
    prepTimeMin = 5,
    cookTimeMin = 10,
    servings = 2,
    calories = 400,
    proteinG = 20.0,
    carbsG = 40.0,
    fatG = 10.0,
    ingredients = emptyList(),
    instructions = emptyList(),
    imageUrl = null,
    source = null,
    tags = null,
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
    favoriteCount = favoriteCount,
    isFavorited = favorited,
)
