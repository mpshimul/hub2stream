package com.shimulfp.hub2stream.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.shimulfp.hub2stream.ui.theme.FocusAccent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> CategoryRow(
    title: String,
    items: List<T>,
    onItemClick: (T) -> Unit,
    onMoreClick: () -> Unit,
    posterContent: @Composable (T, Boolean) -> Unit,  // requestFocus param
    isFirstRow: Boolean = false,
    titleFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val moreButtonFocusRequester = remember { FocusRequester() }
    var isMoreButtonFocused by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            TextButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .focusRequester(moreButtonFocusRequester)
                    .onFocusChanged { focusState -> isMoreButtonFocused = focusState.isFocused }
                    .then(
                        if (isMoreButtonFocused) {
                            Modifier.border(
                                width = 2.dp,
                                color = FocusAccent,
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text("More", color = if (isMoreButtonFocused) FocusAccent else MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = if (isMoreButtonFocused) FocusAccent else MaterialTheme.colorScheme.primary)
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier.focusProperties {
                        up = moreButtonFocusRequester
                    }
                ) {
                    posterContent(item, isFirstRow && index == 0)
                }
            }
        }
    }
}