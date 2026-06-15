package com.escrowmalawi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.escrowmalawi.presentation.navigation.AppNavigation
import com.escrowmalawi.presentation.theme.EscrowMalawiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EscrowMalawiTheme {
                AppNavigation()
            }
        }
    }
}
