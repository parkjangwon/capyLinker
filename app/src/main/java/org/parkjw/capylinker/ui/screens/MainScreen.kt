package org.parkjw.capylinker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.parkjw.capylinker.data.local.LinkItem
import org.parkjw.capylinker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddLink: () -> Unit,
    onNavigateToDetail: (LinkItem) -> Unit
) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CapyLinker") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddLink) {
                Icon(Icons.Default.Add, contentDescription = "Add Link")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (items.isEmpty()) {
                EmptyScreen()
            } else {
                LinkList(
                    items = items,
                    onDeleteItem = { viewModel.deleteItem(it) },
                    onItemClick = onNavigateToDetail
                )
            }
        }
    }
}

@Composable
fun LinkList(items: List<LinkItem>, onDeleteItem: (LinkItem) -> Unit, onItemClick: (LinkItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) {
            LinkItemCard(item = it, onDelete = { onDeleteItem(it) }, onClick = { onItemClick(it) })
        }
    }
}

@Composable
fun EmptyScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "No links saved yet. Share a link to get started!")
    }
}
