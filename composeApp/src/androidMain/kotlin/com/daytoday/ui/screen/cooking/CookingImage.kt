package com.daytoday.ui.screen.cooking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.daytoday.network.absoluteUrl

/**
 * Recipe photos uploaded through the API are stored as root-relative URLs
 * ("/api/recipes/uploads/..."), while seeded recipes use absolute https URLs.
 * Coil needs an absolute URL, so resolve relative ones against the configured server.
 */
internal fun recipeImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.startsWith("/")) absoluteUrl(url) else url
}

@Composable
internal fun RecipeImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = recipeImageUrl(imageUrl),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { RecipeImageFallback(Modifier.fillMaxSize()) },
        error = { RecipeImageFallback(Modifier.fillMaxSize()) },
    )
}

@Composable
internal fun RecipeImageFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.RestaurantMenu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}
