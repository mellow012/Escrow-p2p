package com.escrowmalawi.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.escrowmalawi.presentation.screens.verification.IdentityVerificationScreen
import com.escrowmalawi.presentation.screens.auth.CompleteProfileScreen
import com.escrowmalawi.presentation.screens.auth.LoginScreen
import com.escrowmalawi.presentation.screens.auth.RegisterScreen
import com.escrowmalawi.presentation.screens.escrow.EscrowScreen
import com.escrowmalawi.presentation.screens.home.HomeScreen
import com.escrowmalawi.presentation.screens.marketplace.*
import com.escrowmalawi.presentation.screens.notifications.NotificationsScreen
import com.escrowmalawi.presentation.screens.profile.ProfileScreen
import com.escrowmalawi.presentation.screens.splash.SplashScreen
import com.escrowmalawi.presentation.screens.trade.CreateTradeScreen
import com.escrowmalawi.presentation.screens.trade.TradeDetailScreen
import com.escrowmalawi.presentation.screens.trade.TradeListScreen
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel
import com.escrowmalawi.presentation.viewmodel.TradeViewModel

// ── Route constants ───────────────────────────────────────────────────────────
object Routes {
    const val SPLASH          = "splash"
    const val LOGIN          = "login"
    const val REGISTER       = "register"
    const val COMPLETE_PROFILE = "complete_profile"
    const val HOME           = "home"
    const val MARKETPLACE    = "marketplace"
    const val TRADE_LIST     = "trade_list"
    const val CREATE_TRADE   = "create_trade?sellerUid={sellerUid}&sellerName={sellerName}&sellerPhone={sellerPhone}&desc={desc}&amount={amount}"
    const val TRADE_DETAIL   = "trade_detail/{tradeId}"
    const val ESCROW         = "escrow/{tradeId}"
    const val CREATE_LISTING = "create_listing"
    const val LISTING_DETAIL = "listing_detail/{listingId}"
    const val MY_LISTINGS    = "my_listings"
    const val PROFILE        = "profile"
    const val VERIFY_IDENTITY = "verify_identity"
    const val NOTIFICATIONS  = "notifications"

    fun tradeDetail(id: String)   = "trade_detail/$id"
    fun escrow(id: String)        = "escrow/$id"
    fun listingDetail(id: String) = "listing_detail/$id"
    fun createTrade(
        sellerUid: String = "",
        sellerName: String = "",
        sellerPhone: String = "",
        desc: String = "",
        amount: Double = 0.0
    ) = "create_trade?sellerUid=$sellerUid&sellerName=$sellerName&sellerPhone=$sellerPhone&desc=$desc&amount=$amount"
}

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem(Routes.HOME, Icons.Default.Home, "Home")
    object Marketplace : BottomNavItem(Routes.MARKETPLACE, Icons.Default.Storefront, "Market")
    object Trades : BottomNavItem(Routes.TRADE_LIST, Icons.Default.SwapHoriz, "Trades")
}

// ── Root nav host ─────────────────────────────────────────────────────────────
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom bar on main screens
    val showBottomBar = currentDestination?.route in listOf(
        Routes.HOME, Routes.MARKETPLACE, Routes.TRADE_LIST
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val items = listOf(
                        BottomNavItem.Home,
                        BottomNavItem.Marketplace,
                        BottomNavItem.Trades
                    )
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Splash ────────────────────────────────────────────────────────
            composable(Routes.SPLASH) {
                SplashScreen(
                    onTimeout = {
                        val destination = if (authViewModel.isLoggedIn) Routes.HOME else Routes.LOGIN
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            // ── Auth ──────────────────────────────────────────────────────────
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = { 
                        navController.navigate(Routes.HOME) { 
                            popUpTo(Routes.LOGIN) { inclusive = true } 
                        } 
                    },
                    onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
                )
            }

            composable(Routes.REGISTER) {
                RegisterScreen(
                    onRegistered = { 
                        navController.navigate(Routes.COMPLETE_PROFILE) { 
                            popUpTo(Routes.REGISTER) { inclusive = true } 
                        } 
                    },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable(Routes.COMPLETE_PROFILE) {
                CompleteProfileScreen(
                    onComplete = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.COMPLETE_PROFILE) { inclusive = true }
                        }
                    }
                )
            }

            // ── Home ──────────────────────────────────────────────────────────
            composable(Routes.HOME) {
                val tradeViewModel: TradeViewModel = hiltViewModel()
                val marketplaceViewModel: MarketplaceViewModel = hiltViewModel()
                HomeScreen(
                    onNavigateToMarketplace = { navController.navigate(Routes.MARKETPLACE) },
                    onNavigateToTrades = { navController.navigate(Routes.TRADE_LIST) },
                    onNavigateToMyListings = { navController.navigate(Routes.MY_LISTINGS) },
                    onNavigateToCreateListing = { navController.navigate(Routes.CREATE_LISTING) },
                    onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                    onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onListingClick = { id -> navController.navigate(Routes.listingDetail(id)) },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    authViewModel = authViewModel,
                    tradeViewModel = tradeViewModel,
                    marketplaceViewModel = marketplaceViewModel
                )
            }

            // ── Marketplace ───────────────────────────────────────────────────
            composable(Routes.MARKETPLACE) {
                val marketplaceViewModel: MarketplaceViewModel = hiltViewModel()
                MarketplaceScreen(
                    onListingClick = { id -> navController.navigate(Routes.listingDetail(id)) },
                    onCreateListing = { navController.navigate(Routes.CREATE_LISTING) },
                    onMyTrades = { navController.navigate(Routes.TRADE_LIST) },
                    onMyListings = { navController.navigate(Routes.MY_LISTINGS) },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    viewModel = marketplaceViewModel,
                    authViewModel = authViewModel
                )
            }

            composable(Routes.CREATE_LISTING) {
                CreateListingScreen(
                    onCreated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.LISTING_DETAIL) { backStack ->
                val listingId = backStack.arguments?.getString("listingId") ?: return@composable
                ListingDetailScreen(
                    listingId = listingId,
                    currentUserUid = currentUser?.uid ?: "",
                    onBack = { navController.popBackStack() },
                    onBuyNow = { listing ->
                        navController.navigate(
                            Routes.createTrade(
                                sellerUid = listing.sellerUid,
                                sellerName = listing.sellerName,
                                sellerPhone = listing.sellerPhone,
                                desc = listing.title,
                                amount = listing.price
                            )
                        )
                    }
                )
            }

            composable(Routes.MY_LISTINGS) {
                MyListingsScreen(
                    onBack = { navController.popBackStack() },
                    onListingClick = { id -> navController.navigate(Routes.listingDetail(id)) }
                )
            }

            // ── Trades ────────────────────────────────────────────────────────
            composable(Routes.TRADE_LIST) {
                TradeListScreen(
                    onTradeClick = { id -> navController.navigate(Routes.tradeDetail(id)) },
                    onCreateTrade = { navController.navigate(Routes.CREATE_TRADE) }
                )
            }

            composable(
                route = Routes.CREATE_TRADE,
                arguments = listOf(
                    navArgument("sellerUid") { defaultValue = "" },
                    navArgument("sellerName") { defaultValue = "" },
                    navArgument("sellerPhone") { defaultValue = "" },
                    navArgument("desc") { defaultValue = "" },
                    navArgument("amount") { defaultValue = "0.0" }
                )
            ) { backStack ->
                CreateTradeScreen(
                    initialSellerUid = backStack.arguments?.getString("sellerUid") ?: "",
                    initialSellerName = backStack.arguments?.getString("sellerName") ?: "",
                    initialSellerPhone = backStack.arguments?.getString("sellerPhone") ?: "",
                    initialDescription = backStack.arguments?.getString("desc") ?: "",
                    initialAmount = backStack.arguments?.getString("amount")?.toDoubleOrNull() ?: 0.0,
                    onTradeCreated = { id ->
                        navController.navigate(Routes.tradeDetail(id)) {
                            popUpTo(Routes.CREATE_TRADE) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.TRADE_DETAIL) { backStack ->
                val tradeId = backStack.arguments?.getString("tradeId") ?: return@composable
                TradeDetailScreen(
                    tradeId = tradeId,
                    currentUserUid = currentUser?.uid ?: "",
                    onBack = { navController.popBackStack() },
                    onGoToEscrow = { id -> navController.navigate(Routes.escrow(id)) }
                )
            }

            composable(Routes.ESCROW) { backStack ->
                val tradeId = backStack.arguments?.getString("tradeId") ?: return@composable
                EscrowScreen(
                    tradeId = tradeId,
                    currentUserUid = currentUser?.uid ?: "",
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Profile & Notifications ──────────────────────────────────────
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onVerifyClick = { navController.navigate(Routes.VERIFY_IDENTITY) }
                )
            }

            composable(Routes.VERIFY_IDENTITY) {
                IdentityVerificationScreen(
                    onBack = { navController.popBackStack() },
                    onComplete = {
                        navController.popBackStack()
                        // In real app, we would update the user state in Firestore
                    }
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToTradeDetail = { tradeId ->
                        navController.navigate(Routes.tradeDetail(tradeId))
                    },
                    onNavigateToEscrow = { tradeId ->
                        navController.navigate(Routes.escrow(tradeId))
                    }
                )
            }
        }
    }
}
