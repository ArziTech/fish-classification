package com.example.fishclassification.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stub Result screen — Task 5 will replace this with the full inference UI.
 * Displays the received image URI so the navigation flow can be verified end-to-end.
 */
@Composable
fun ResultScreen(imageUri: String, onBack: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Result will appear here (Task 5)",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Image URI:",
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = imageUri,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onBack) {
                Text(text = "Back")
            }
        }
    }
}
