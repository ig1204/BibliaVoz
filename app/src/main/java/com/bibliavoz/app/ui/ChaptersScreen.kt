package com.bibliavoz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.player.PlayerBus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    viewModel: MainViewModel,
    bookNumber: Int,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    val books by viewModel.books.collectAsStateWithLifecycle()
    val playerState by PlayerBus.state.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.number == bookNumber }
    val chapterCount = book?.chapterCount ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 64.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items((1..chapterCount).toList(), key = { it }) { chapter ->
                val isCurrent = playerState.position.book == bookNumber &&
                    playerState.position.chapter == chapter

                Surface(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    onClick = { onOpenChapter(chapter) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = chapter.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
