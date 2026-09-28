package com.bibliavoz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.R
import com.bibliavoz.app.data.BookInfo
import com.bibliavoz.app.player.PlaybackService
import java.text.Normalizer
import java.util.Locale

/** Quita acentos para que "genesis" encuentre "Génesis". */
private fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    viewModel: MainViewModel,
    onOpenBook: (Int) -> Unit,
    onOpenReader: (Int, Int) -> Unit,
    onOpenLecturas: () -> Unit,
    ensureNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val books by viewModel.books.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var showVozIa by remember { mutableStateOf(false) }

    val normalizedQuery = query.trim().foldForSearch()
    val searching = normalizedQuery.isNotEmpty()

    val visibleBooks = remember(books, normalizedQuery, tab) {
        books.filter { book ->
            val matchesQuery = !searching ||
                book.name.foldForSearch().contains(normalizedQuery) ||
                book.abbr.foldForSearch().startsWith(normalizedQuery)
            val matchesTab = searching || (if (tab == 0) book.isOldTestament else !book.isOldTestament)
            matchesQuery && matchesTab
        }
    }

    if (showSettings) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
            onOpenVoicePicker = { showSettings = false; showVoicePicker = true },
            onOpenVozIa = { showSettings = false; showVozIa = true },
        )
    }
    if (showVoicePicker) {
        VoicePickerSheet(viewModel = viewModel, onDismiss = { showVoicePicker = false })
    }
    if (showVozIa) {
        VozIaSheet(viewModel = viewModel, onDismiss = { showVozIa = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = viewModel.translationName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .clickable(onClick = onOpenLecturas),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Lecturas de la misa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "En el orden de la Iglesia, día por día",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Icon(
                        Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            ContinueCard(
                viewModel = viewModel,
                onListen = {
                    ensureNotificationPermission()
                    val position = viewModel.savedPosition
                    PlaybackService.seek(context, position, autoPlay = true)
                    onOpenReader(position.book, position.chapter)
                },
                onOpen = {
                    val position = viewModel.savedPosition
                    onOpenReader(position.book, position.chapter)
                },
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar libro…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Borrar")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            if (!searching) {
                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("Antiguo Testamento") },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("Nuevo Testamento") },
                    )
                }
            }

            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleBooks, key = { it.number }) { book ->
                        BookRow(book = book, onClick = { onOpenBook(book.number) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (visibleBooks.isEmpty()) {
                        item {
                            Text(
                                text = "Ningún libro coincide con «$query».",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: BookInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = book.abbr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(book.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${book.chapterCount} capítulo${if (book.chapterCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContinueCard(
    viewModel: MainViewModel,
    onListen: () -> Unit,
    onOpen: () -> Unit,
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    if (books.isEmpty()) return

    val position = viewModel.savedPosition
    val bookName = viewModel.bookName(position.book)
    if (bookName.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Continuar escuchando",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = position.reference(bookName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            FilledIconButton(onClick = onListen, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Escuchar")
            }
        }
    }
}
