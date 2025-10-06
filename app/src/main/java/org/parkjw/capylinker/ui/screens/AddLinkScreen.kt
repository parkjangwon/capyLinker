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
import org.parkjw.capylinker.viewmodel.LinkReceiverViewModel

@Composable
fun AddLinkScreen(viewModel: LinkReceiverViewModel, onLinkAdded: () -> Unit) {
    var url by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Enter a URL to save and analyze")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            viewModel.saveLink(url)
            onLinkAdded()
        }) {
            Text("Save Link")
        }
    }
}
