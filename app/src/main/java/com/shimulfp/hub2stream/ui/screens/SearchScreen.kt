package com.shimulfp.hub2stream.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shimulfp.hub2stream.extractor.models.MediaItemPreview
import com.shimulfp.hub2stream.ui.components.MoviePoster
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: SearchViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(application) as T
            }
        }
    )

    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val gridState = rememberLazyGridState()
    val searchFieldFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // Track whether our keyboard is "open" (field is focused and we haven't manually hidden it)
    var isKeyboardOpen by remember { mutableStateOf(true) }

    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 8 else 4

    // Auto-focus search field on enter
    LaunchedEffect(Unit) {
        searchFieldFocusRequester.requestFocus()
        isKeyboardOpen = true
    }

    // Debounced auto-search: fires 600ms after last keystroke, minimum 2 chars
    LaunchedEffect(query) {
        if (query.length >= 2) {
            kotlinx.coroutines.delay(600)
            viewModel.search(query)
        }
    }

    // Auto-scroll grid to top when new results arrive
    LaunchedEffect(searchResults.size) {
        if (searchResults.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    // BACK button: if keyboard open → hide keyboard & focus first result; else → navigate back
    BackHandler(enabled = isKeyboardOpen) {
        keyboardController?.hide()
        isKeyboardOpen = false
        if (searchResults.isNotEmpty()) {
            scope.launch { gridFocusRequester.requestFocus() }
        }
    }

    BackHandler(enabled = !isKeyboardOpen) {
        navController.popBackStack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        // ---- Search Bar ----
        Surface(
            color = Color(0xFF1A1A1A),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button — always navigates back (not hide keyboard)
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(searchFieldFocusRequester),
                    placeholder = { Text("Search movies or series...", color = Color(0xFF666666)) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = FocusAccent,
                        focusedBorderColor = FocusAccent,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedContainerColor = Color(0xFF2A2A2A),
                        unfocusedContainerColor = Color(0xFF2A2A2A)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank() && !isLoading) {
                                viewModel.search(query)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, "Clear", tint = Color(0xFF888888))
                            }
                        }
                    }
                )

                // Search / Loading button
                IconButton(
                    onClick = {
                        if (query.isNotBlank() && !isLoading) viewModel.search(query)
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = FocusAccent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Search, "Search", tint = FocusAccent)
                    }
                }
            }
        }

        // ---- Content ----
        Box(
            modifier = Modifier.fillMaxSize().imePadding()
        ) {
            when {
                isLoading && searchResults.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FocusAccent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Searching...", color = Color(0xFF888888), fontSize = 14.sp)
                    }
                }
                error != null && searchResults.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error", color = Color(0xFFCC4444), fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Try again", color = Color(0xFF888888), fontSize = 14.sp)
                    }
                }
                searchResults.isEmpty() && query.length == 1 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Keep typing...", color = Color(0xFF555555), fontSize = 16.sp)
                }
                searchResults.isEmpty() && query.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, null, tint = Color(0xFF333333), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Search movies & series", color = Color(0xFF555555), fontSize = 18.sp)
                    }
                }
                searchResults.isEmpty() && query.isNotBlank() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, null, tint = Color(0xFF444444), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No results found", color = Color(0xFF888888), fontSize = 18.sp)
                        Text("for \"$query\"", color = Color(0xFF666666), fontSize = 14.sp)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().focusRequester(gridFocusRequester),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(searchResults, key = { index, item -> "${item.slug}_$index" }) { index, item ->
                            MoviePoster(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is MediaItemPreview.MoviePreview -> navController.navigate(Screen.MovieDetail.pass(item.slug))
                                        is MediaItemPreview.SeriesPreview -> navController.navigate(Screen.SeriesDetail.pass(item.slug))
                                    }
                                },
                                requestFocus = index == 0 && !isKeyboardOpen
                            )
                        }
                    }

                    // Result count badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    ) {
                        Text(
                            "${searchResults.size} results",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
