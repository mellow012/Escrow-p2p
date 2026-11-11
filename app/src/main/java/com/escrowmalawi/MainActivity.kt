package com.escrowmalawi

import android.content.Intent
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
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val nav = rememberNavController()
            navController = nav
            EscrowMalawiTheme {
                AppNavigation(navController = nav)
            }
        }
    }

    // singleTask: when the app is already open, the deep link arrives here,
    // NOT in onCreate. Without setIntent() the old intent was kept and the
    // link was ignored.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "escrowmalawi" || uri.host != "payment") return
        val tradeId = uri.getQueryParameter("tradeId")
        val status = uri.getQueryParameter("status") // "success" | "cancelled"
        val nav = navController
        if (tradeId != null && status == "success" && nav != null) {
            nav.navigate(Routes.tradeDetail(tradeId)) {
                popUpTo(Routes.HOME)
            }
        }
        // consume so it doesn't re-trigger on the next resume
        intent.data = null
    }
}