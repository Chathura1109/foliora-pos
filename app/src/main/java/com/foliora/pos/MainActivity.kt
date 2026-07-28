package com.foliora.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.foliora.pos.ui.theme.FolioraTheme
import androidx.navigation.compose.rememberNavController
import com.foliora.pos.ui.navigation.FolioraNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * @AndroidEntryPoint tells Hilt: "This Activity participates in dependency injection."
 * Without this, you cannot inject anything into this Activity or its Composable screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolioraTheme {
                val navController = rememberNavController()
                FolioraNavGraph(navController = navController)
            }
        }
    }
}