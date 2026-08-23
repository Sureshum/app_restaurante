package com.example.restaurantepos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.restaurantepos.data.AppDatabase
import com.example.restaurantepos.ui.MenuManagementScreen
import com.example.restaurantepos.ui.OrderScreen
import com.example.restaurantepos.ui.PosViewModel
import com.example.restaurantepos.ui.ProductManagementScreen
import com.example.restaurantepos.ui.TableDashboardScreen
import com.example.restaurantepos.ui.UserSelectionScreen

fun NavController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.posDao()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: PosViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return PosViewModel(dao) as T
                            }
                        }
                    )

                    RestaurantAppNavHost(viewModel = viewModel)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun RestaurantAppNavHost(viewModel: PosViewModel) {
    val navController = rememberNavController()
    val users by viewModel.users.collectAsState()
    val session by viewModel.currentUserSession.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            UserSelectionScreen(
                users = users,
                onAuthenticate = { user, pin, onSuccess, onError ->
                    viewModel.authenticate(user, pin, onSuccess = {
                        onSuccess()
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    }, onError = onError)
                },
                onCreateUser = { name, pin, avatarUri, role ->
                    viewModel.createUser(name, pin, avatarUri, role)
                },
                onDeleteUser = { user, pin, onSuccess, onError ->
                    viewModel.deleteUser(user, pin, onSuccess, onError)
                }
            )
        }

        composable("dashboard") {
            val areas by viewModel.areas.collectAsState()
            val selectedAreaId by viewModel.selectedAreaId.collectAsState()
            val tables by viewModel.currentTables.collectAsState()
            val products by viewModel.products.collectAsState()

            TableDashboardScreen(
                viewModel = viewModel,
                session = session,
                areas = areas,
                selectedAreaId = selectedAreaId,
                tables = tables,
                products = products,
                onSelectArea = { areaId -> viewModel.selectArea(areaId) },
                onAddTable = { viewModel.addTable() },
                onRemoveTable = { viewModel.removeTable() },
                onSetTableCount = { count -> viewModel.setTableCount(count) },
                onTableClick = { tableId ->
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate("order_screen/$tableId")
                    }
                },
                onCreateArea = { name, prefix -> viewModel.createArea(name, prefix) },
                onDeleteArea = { area -> viewModel.deleteArea(area) },
                onCreateProduct = { cat, name, price -> viewModel.createProduct(cat, name, price) },
                onOpenSystemMenu = {
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate("menu_management")
                    }
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("product_management") {
            ProductManagementScreen(
                onCreateProduct = { category, name, price, imageUri ->
                    viewModel.createProduct(category, name, price, imageUri)
                },
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable("menu_management") {
            val products by viewModel.products.collectAsState(initial = emptyList())
            val userSession by viewModel.currentUserSession.collectAsState(initial = null)
            val isAdmin = userSession?.isAdmin() ?: false

            MenuManagementScreen(
                isAdmin = isAdmin,
                products = products,
                onUpdateProduct = { updatedProduct ->
                    viewModel.updateProduct(updatedProduct)
                },
                onAddProductClick = {
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate("product_management")
                    }
                },
                onBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable(
            route = "order_screen/{tableId}",
            arguments = listOf(navArgument("tableId") { type = NavType.IntType })
        ) { backStackEntry ->
            val tableId = backStackEntry.arguments?.getInt("tableId") ?: 0
            val tables by viewModel.currentTables.collectAsState()
            val products by viewModel.products.collectAsState()
            val orderItems by viewModel.getOrderItemsForTable(tableId)
                .collectAsState(initial = emptyList())

            val targetTable = tables.find { it.id == tableId }
            val currentWaiterName = session?.userName ?: "Camarero"

            if (targetTable != null) {
                OrderScreen(
                    table = targetTable,
                    products = products,
                    existingOrderItems = orderItems,
                    waiterName = currentWaiterName,
                    onSaveOrder = { items, total ->
                        viewModel.saveOrderForTable(targetTable.id, items, total)
                    },
                    onPayTable = { items, total ->
                        viewModel.payTableDirectly(targetTable.id, items, total)
                    },
                    onBack = {
                        navController.safePopBackStack()
                    }
                )
            } else {
                navController.safePopBackStack()
            }
        }
    }
}