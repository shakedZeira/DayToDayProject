package com.daytoday.ui.screen.nba

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.daytoday.model.NbaNews
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.EmptyState
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay
import com.squareup.okhttp3.OkHttpClient
import com.squareup.okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbaNewsScreen(
    viewModel: NbaNewsViewModel = hiltViewModel(),
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "NBA News",
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.Home.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            NbaNewsTabContent(viewModel = viewModel)
        }
    }
}

@Composable
fun NbaNewsTabContent(
    viewModel: NbaNewsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val news by viewModel.news.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = news) {
            is UiState.Loading -> {
                LoadingOverlay("Loading news...")
            }
            is UiState.Error -> {
                ErrorState(state.message, viewModel::refresh)
            }
            is UiState.Success -> {
                val articles = state.data
                NewsContent(articles)
            }
            is UiState.Stale -> {
                val articles = state.data
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stale data indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Stale data",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Showing cached news — pull to refresh",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    NewsContent(articles)
                }
            }
        }
    }
}

@Composable
private fun NewsContent(articles: List<NbaNews>) {
    if (articles.isEmpty()) {
        EmptyState(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    contentDescription = "News",
                    modifier = Modifier.size(64.dp)
                )
            },
            title = "No news",
            subtitle = "No NBA news available at the moment"
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(articles) { article ->
                DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                    NewsCard(article = article)
                }
            }
        }
    }
}

@Composable
private fun NewsCard(article: NbaNews) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Image if available
        article.imageUrl?.let { imageUrl ->
            NewsImage(url = imageUrl, contentDescription = article.title)
        }

        // Title
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Summary
        Text(
            text = article.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        // Source and date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDate(article.publishedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Read more button
            val uriHandler = LocalUriHandler.current
            DayTodayButton(
                text = "Read More",
                onClick = {
                    if (article.url.isNotBlank()) {
                        runCatching { uriHandler.openUri(article.url) }
                    }
                },
                buttonType = ButtonType.Text
            )
        }
    }
}

private const val NEWS_IMAGE_CACHE_LIMIT = 8
private const val NEWS_IMAGE_DECODE_TARGET_WIDTH = 1080

private val newsImageHttpClient = OkHttpClient()
private val newsImageCache = LinkedHashMap<String, ImageBitmap>()

private sealed interface NewsImageState {
    data object Loading : NewsImageState
    data class Loaded(val image: ImageBitmap) : NewsImageState
    data object Failed : NewsImageState
}

@Composable
private fun NewsImage(url: String, contentDescription: String) {
    val state by produceState<NewsImageState>(
        initialValue = NewsImageState.Loading,
        key1 = url
    ) {
        val cached = synchronized(newsImageCache) { newsImageCache[url] }
        if (cached != null) {
            value = NewsImageState.Loaded(cached)
            return@produceState
        }
        val image = withContext(Dispatchers.IO) { downloadNewsImage(url) }
        if (image == null) {
            value = NewsImageState.Failed
            return@produceState
        }
        synchronized(newsImageCache) {
            newsImageCache[url] = image
            while (newsImageCache.size > NEWS_IMAGE_CACHE_LIMIT) {
                newsImageCache.remove(newsImageCache.keys.first())
            }
        }
        value = NewsImageState.Loaded(image)
    }
    when (val current = state) {
        NewsImageState.Loading -> NewsImageFallback()
        NewsImageState.Failed -> Unit
        is NewsImageState.Loaded -> Image(
            bitmap = current.image,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun downloadNewsImage(url: String): ImageBitmap? = try {
    val request = Request.Builder().url(url).build()
    newsImageHttpClient.newCall(request).execute().use { response ->
        val bytes = response.body?.bytes()
        if (response.isSuccessful && bytes != null) decodeNewsImage(bytes) else null
    }
} catch (_: Exception) {
    null
}

private fun decodeNewsImage(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= NEWS_IMAGE_DECODE_TARGET_WIDTH) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return bitmap.asImageBitmap()
}

@Composable
private fun NewsImageFallback() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
    }
}

private fun formatDate(timestamp: Long): String =
    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))