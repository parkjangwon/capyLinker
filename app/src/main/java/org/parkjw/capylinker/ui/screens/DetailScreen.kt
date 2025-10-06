package org.parkjw.capylinker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.parkjw.capylinker.data.local.LinkItem
import org.parkjw.capylinker.viewmodel.MainViewModel

@Composable
fun DetailScreen(item: LinkItem, viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    var tags by remember { mutableStateOf(item.tags.joinToString(", ")) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = item.url, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.summary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tags (comma-separated)") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val updatedItem = item.copy(tags = tags.split(",").map { it.trim() })
            viewModel.updateItem(updatedItem)
            onNavigateBack()
        }) {
            Text("Save")
        }
    }
}
