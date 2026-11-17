package com.daytoday.ui.screen.cooking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.daytoday.network.RecipeView
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.ButtonType

@Composable
fun CookingScreen(
    navController: NavHostController,
    viewModel: CookingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DayTodayTopAppBar(title = "בישול")

    when (val state = uiState) {
        is UiState.Loading -> LoadingOverlay("טוען מתכונים...")
        is UiState.Error -> ErrorState(state.message, viewModel::refresh)
        is UiState.Success -> {
            CookingContent(
                recipes = state.data.recipes,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onMealClick = { viewModel.filterByMeal(it) },
                onFavoritesClick = { viewModel.showFavorites() },
                selectedMeal = state.data.selectedMeal,
                showFavorites = state.data.showFavorites,
                onRecipeClick = { recipe ->
                    navController.navigate("${Screen.RecipeDetail.route}/${recipe.id}")
                },
            )
        }
    }
}

@Composable
private fun CookingContent(
    recipes: List<RecipeView>,
    onToggleFavorite: (RecipeView) -> Unit,
    onMealClick: (String?) -> Unit,
    onFavoritesClick: () -> Unit,
    selectedMeal: String?,
    showFavorites: Boolean,
    onRecipeClick: (RecipeView) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Meal filter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealFilterChip(
                label = "הכל",
                selected = selectedMeal == null && !showFavorites,
                onClick = { onMealClick(null) },
            )
            MealFilterChip(
                label = "ארוחת בוקר",
                selected = selectedMeal == "breakfast",
                onClick = { onMealClick("breakfast") },
            )
            MealFilterChip(
                label = "ארוחת צהריים",
                selected = selectedMeal == "lunch",
                onClick = { onMealClick("lunch") },
            )
            MealFilterChip(
                label = "ארוחת ערב",
                selected = selectedMeal == "dinner",
                onClick = { onMealClick("dinner") },
            )
            MealFilterChip(
                label = "נשנוש",
                selected = selectedMeal == "snack",
                onClick = { onMealClick("snack") },
            )
            MealFilterChip(
                label = "מועדפים",
                selected = showFavorites,
                onClick = { onFavoritesClick() },
            )
        }

        if (recipes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Recipes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "אין מתכונים זמינים",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showFavorites) "עדיין אין מתכונים במועדפים" else "נסה סינון אחר",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            ) {
                items(
                    items = recipes,
                    key = { it.id },
                ) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe) },
                        onToggleFavorite = { onToggleFavorite(recipe) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MealFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeView,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val isFavorited = recipe.isFavorited

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            RecipeImage(
                imageUrl = recipe.imageUrl,
                contentDescription = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorited) "הסר ממועדפים" else "הוסף למועדפים",
                        tint = if (isFavorited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealBadge(meal = recipe.meal)
                DifficultyBadge(difficulty = recipe.difficulty)
                TimeBadge(prepMin = recipe.prepTimeMin, cookMin = recipe.cookTimeMin)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroChip(label = "קלוריות", value = "${recipe.calories.toInt()}")
                MacroChip(label = "חלבון", value = "${recipe.proteinG.toInt()}ג׳")
                MacroChip(label = "פחמימות", value = "${recipe.carbsG.toInt()}ג׳")
                MacroChip(label = "שומן", value = "${recipe.fatG.toInt()}ג׳")
            }

            recipe.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            }
        }
    }
}

@Composable
private fun MealBadge(meal: String) {
    val label = when (meal) {
        "breakfast" -> "ארוחת בוקר"
        "lunch" -> "ארוחת צהריים"
        "dinner" -> "ארוחת ערב"
        "snack" -> "נשנוש"
        else -> meal
    }
    Surface(
        modifier = Modifier.size(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DifficultyBadge(difficulty: String) {
    val label = when (difficulty) {
        "beginner" -> "מתחיל"
        "intermediate" -> "בינוני"
        "advanced" -> "מתקדם"
        else -> difficulty
    }
    Surface(
        modifier = Modifier.size(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TimeBadge(prepMin: Int, cookMin: Int) {
    Surface(
        modifier = Modifier.size(32.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = "$prepMin דק׳ הכנה, $cookMin דק׳ בישול",
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MacroChip(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
