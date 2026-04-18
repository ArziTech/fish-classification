package com.example.fishclassification

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fishclassification.navigation.AppNavigation
import com.example.fishclassification.ui.theme.FishClassificationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FishClassificationTheme {
                AppNavigation()
            }
        }
    }
}
