package com.shimulfp.hub2stream.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shimulfp.hub2stream.extractor.models.MediaItemPreview
import com.shimulfp.hub2stream.ui.components.MoviePoster
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.utils.CategoryCache
import com.shimulfp.hub2stream.viewmodels.CategoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    navController: NavController,
    title: String,
    isSeries: Boolean,
    category: String? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: CategoryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(application) as T
            }
        }
    )

    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMsg by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 8 else 4

    val gridState = rememberLazyGridState()

    // Detect when we should load more (near bottom of list)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount

            // Load more when we're within 6 items of the end
            val result = lastVisibleItem != null &&
                lastVisibleItem.index >= totalItems - 6 &&
                hasMore &&
                !isLoadingMore &&
                !isLoading

            if (result) {
                Log.d("CategoryScreen", "shouldLoadMore=true - lastVisible=${lastVisibleItem?.index}, total=$totalItems, hasMore=$hasMore, isLoadingMore=$isLoadingMore")
            }

            result
        }
    }

    // Trigger load more when shouldLoadMore becomes true
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            Log.d("CategoryScreen", "Triggering loadMore()")
            viewModel.loadMore()
        }
    }

    // Load data when screen opens
    LaunchedEffect(isSeries, category) {
        if (isSeries) {
            viewModel.loadSeriesByCategory(category)
        } else {
            // For movies/other categories, check if we have category metadata
            val categoryType = CategoryCache.categoryType
            val categoryData = CategoryCache.categoryData
            val cachedItems = CategoryCache.currentItems

            if (categoryType != null && categoryData != null) {
                // Use pagination for categories with metadata
                viewModel.loadCategoryByType(categoryType, categoryData, cachedItems)
            } else {
                // Fallback to cached items without pagination
                viewModel.setItems(cachedItems)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Debug info for pagination
            /** if (!isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Items: ${items.size} | Page: ${viewModel.getCurrentPage()} | HasMore: $hasMore | LoadingMore: $isLoadingMore",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (hasMore && !isLoadingMore && viewModel.getCurrentPage() < 50) {
                            Button(
                                onClick = {
                                    Log.d("CategoryScreen", "Manual loadMore button clicked")
                                    viewModel.loadMore()
                                },
                                enabled = hasMore && !isLoadingMore
                            ) {
                                Text("Load More")
                            }
                        }
                    }
                }
            }
            **/

            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                errorMsg != null && items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $errorMsg")
                        Button(onClick = { if (isSeries) viewModel.loadSeriesByCategory(category) else viewModel.setItems(CategoryCache.currentItems) }) {
                            Text("Retry")
                        }
                    }
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(if (isSeries) "No series found." else "No movies available.")
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, if (isLoadingMore || hasMore) 80.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { item ->
                            MoviePoster(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is MediaItemPreview.MoviePreview -> navController.navigate(Screen.MovieDetail.pass(item.slug))
                                        is MediaItemPreview.SeriesPreview -> navController.navigate(Screen.SeriesDetail.pass(item.slug))
                                    }
                                },
                                requestFocus = items.indexOf(item) == 0
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}