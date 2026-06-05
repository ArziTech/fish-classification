package com.example.fishclassification.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fishclassification.ml.ModelCatalog
import com.example.fishclassification.ml.ModelOption
import com.example.fishclassification.ui.components.ImageSourcePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToResult: (imageUri: String, modelAsset: String, useGpu: Boolean) -> Unit,
    onOpenLogs: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(ModelCatalog.default) }
    var useGpu by remember { mutableStateOf(true) }
    val gpuAllowed = selectedModel.supportsGpu

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fish Classification") },
                actions = {
                    TextButton(onClick = onOpenLogs) { Text("Logs") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModelPickerDropdown(
                selected = selectedModel,
                options = ModelCatalog.options,
                onSelect = { selectedModel = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Use GPU",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = when {
                            !gpuAllowed -> "Not supported for INT8/QAT models"
                            useGpu -> "GPU delegate (falls back to CPU if unsupported)"
                            else -> "CPU only"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!gpuAllowed)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useGpu && gpuAllowed,
                    onCheckedChange = { if (gpuAllowed) useGpu = it },
                    enabled = gpuAllowed,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showPicker = true },
            ) {
                Text(text = "Inference")
            }
        }
    }

    if (showPicker) {
        ImageSourcePicker(
            onImageSelected = { uri ->
                showPicker = false
                onNavigateToResult(uri.toString(), selectedModel.assetFileName, useGpu)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerDropdown(
    selected: ModelOption,
    options: List<ModelOption>,
    onSelect: (ModelOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
