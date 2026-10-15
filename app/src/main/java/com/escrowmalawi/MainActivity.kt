package com.escrowmalawi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.escrowmalawi.presentation.navigation.Routes
import com.escrowmalawi.presentation.navigation.AppNavigation
import com.escrowmalawi.presentation.theme.EscrowMalawiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            navController = rememberNavController()
            EscrowMalawiTheme {
                AppNavigation(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Handle deep links from PayChangu or other sources
        intent?.data?.let { uri ->
            if (uri.scheme == "escrowmalawi" && uri.host == "payment") {
                val tradeId = uri.getQueryParameter("tradeId")
                val status = uri.getQueryParameter("status") // e.g., "success", "cancelled"
                
                if (tradeId != null) {
                    if (status == "success") {
                        // Navigate to trade detail or escrow success screen
                        navController.navigate(Routes.tradeDetail(tradeId)) {
                            popUpTo(Routes.HOME)
                        }
                    }
                    // Clear the intent data so it doesn't trigger again
                    intent.data = null
                }
            }
        }
    }
}
