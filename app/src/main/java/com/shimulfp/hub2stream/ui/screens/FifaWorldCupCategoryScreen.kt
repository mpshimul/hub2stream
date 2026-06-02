package com.shimulfp.hub2stream.ui.screens

import android.content.res.Configuration
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shimulfp.hub2stream.viewmodels.FifaWorldCupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FifaWorldCupCategoryScreen(
    navController: NavController,
    viewModel: FifaWorldCupViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMsg by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 2 else 1

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
                Log.d("FifaWorldCupScreen", "shouldLoadMore=true - lastVisible=${lastVisibleItem?.index}, total=$totalItems, hasMore=$hasMore, isLoadingMore=$isLoadingMore")
            }

            result
        }
    }

    // Trigger load more when shouldLoadMore becomes true
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            Log.d("FifaWorldCupScreen", "Triggering loadMore()")
            viewModel.loadMore()
        }
    }

    // Load data when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadMatches()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FIFA World Cup") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMsg != null && items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: $errorMsg")
                    Button(onClick = { viewModel.loadMatches() }) {
                        Text("Retry")
                    }
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("No upcoming FIFA World Cup matches found.")
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, if (isLoadingMore || hasMore) 80.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { match ->
                        UpcomingMatchCard(
                            match = match,
                            orientation = if (isLandscape) CardOrientation.Landscape else CardOrientation.Portrait,
                            onClick = { }
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