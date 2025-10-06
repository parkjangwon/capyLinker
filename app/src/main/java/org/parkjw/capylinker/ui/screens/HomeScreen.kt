
package org.parkjw.capylinker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.parkjw.capylinker.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit
) {
    val links by viewModel.links.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val expandedLinkUrl = remember { mutableStateOf<String?>(null) }
    val showContextMenu = remember { mutableStateOf<Link?>(null) }

    val language by viewModel.language.collectAsState(initial = "en")
    val strings = remember(language) {
        org.parkjw.capylinker.ui.strings.getStrings(language)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CapyLinker") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
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

    if (showAddDialog) {
        AddLinkDialog(
            strings = strings,
            onDismiss = { viewModel.hideAddLinkDialog() },
            onSave = { url -> viewModel.saveLink(url) }
        )
    }

    showContextMenu.value?.let { link ->
        LinkContextMenu(
            link = link,
            onDismiss = { showContextMenu.value = null },
            onCopyUrl = { 
                // Implement copy to clipboard
                showContextMenu.value = null
            },
            onShare = {
                // Implement share
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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

@Composable
private fun TagChip(tag: String) {
    SuggestionChip(
        onClick = { },
        label = { Text(tag) }
    )
}

data class Link(
    val title: String,
    val url: String,
    val summary: String,
    val tags: List<String>,
    val isAnalyzing: Boolean = false
)
