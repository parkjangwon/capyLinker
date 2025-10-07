
package org.parkjw.capylinker.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.parkjw.capylinker.viewmodel.HomeViewModel

data class Link(
    val title: String,
    val url: String,
    val summary: String,
    val tags: List<String>,
    val isAnalyzing: Boolean = false,
    val thumbnailUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val links by viewModel.links.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showClipboardDialog by viewModel.showClipboardDialog.collectAsState()
    val clipboardUrl by viewModel.clipboardUrl.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val expandedLinkUrl = remember { mutableStateOf<String?>(null) }
    val showContextMenu = remember { mutableStateOf<Link?>(null) }

    val language by viewModel.language.collectAsState(initial = "en")
    val strings = remember(language) {
        org.parkjw.capylinker.ui.strings.getStrings(language)
    }

    // 뒤로가기 처리
    val backPressedTime = remember { mutableStateOf(0L) }

    BackHandler(enabled = true) {
        when {
            isSearchActive -> {
                // 검색 모드에서는 검색 종료
                viewModel.toggleSearchActive()
            }
            showAddDialog || showClipboardDialog || showContextMenu.value != null -> {
                // 다이얼로그가 열려있으면 닫기
                viewModel.hideAddLinkDialog()
                viewModel.dismissClipboardDialog()
                showContextMenu.value = null
            }
            else -> {
                // 목록 화면에서 두 번 눌러야 종료
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime.value < 2000) {
                    // 2초 이내에 다시 누르면 종료
                    (context as? android.app.Activity)?.finish()
                } else {
                    backPressedTime.value = currentTime
                    Toast.makeText(context, strings.pressBackAgainToExit, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 클립보드 확인
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString()
            viewModel.checkClipboard(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(strings.searchPlaceholder) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    } else {
                        Text("CapyLinker")
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { viewModel.toggleSearchActive() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { viewModel.toggleSearchActive() }) {
                            Icon(Icons.Default.Search, contentDescription = strings.search)
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddLinkDialog() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Link"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tag Filter
            if (allTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { viewModel.selectTag(null) },
                            label = { Text("All") }
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { 
                                viewModel.selectTag(if (selectedTag == tag) null else tag)
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            // Links List
            if (links.isEmpty() && searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.noResults,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(links, key = { it.url }) { link ->
                        LinkItem(
                            link = link,
                            isExpanded = expandedLinkUrl.value == link.url,
                            onClick = {
                                expandedLinkUrl.value = if (expandedLinkUrl.value == link.url) null else link.url
                            },
                            onLongClick = { showContextMenu.value = link },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddLinkDialog(
            strings = strings,
            onDismiss = { viewModel.hideAddLinkDialog() },
            onSave = { url -> viewModel.saveLink(url) }
        )
    }

    if (showClipboardDialog && clipboardUrl != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClipboardDialog() },
            title = { Text(strings.clipboardDetected) },
            text = {
                Column {
                    Text(strings.clipboardMessage)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = clipboardUrl!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptClipboardUrl() }) {
                    Text(strings.add)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClipboardDialog() }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    showContextMenu.value?.let { link ->
        LinkContextMenu(
            link = link,
            strings = strings,
            onDismiss = { showContextMenu.value = null },
            onOpen = {
                showContextMenu.value = null
            },
            onCopyUrl = { 
                showContextMenu.value = null
            },
            onShare = {
                showContextMenu.value = null
            },
            onDelete = {
                viewModel.deleteLink(link)
                showContextMenu.value = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun LinkItem(
    link: Link,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 썸네일 이미지
            if (!link.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = link.thumbnailUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(end = 12.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = link.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (link.title.contains("Error") || link.title.contains("Quota"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (link.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = link.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    link.tags.forEach { tag ->
                        TagChip(tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    SuggestionChip(
        onClick = { },
        label = { Text(tag) }
    )
}