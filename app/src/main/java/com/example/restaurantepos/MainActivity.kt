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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.restaurantepos.data.AppDatabase
import com.example.restaurantepos.ui.OrderScreen
import com.example.restaurantepos.ui.PosViewModel
import com.example.restaurantepos.ui.PosViewModelFactory
import com.example.restaurantepos.ui.ProductManagementScreen
import com.example.restaurantepos.ui.TableDashboardScreen
import com.example.restaurantepos.ui.UserSelectionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.posDao()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: PosViewModel = viewModel(
                        factory = PosViewModelFactory(dao)
                    )

                    RestaurantAppNavHost(viewModel = viewModel)
                }
            }
        }
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
                session = session,
                areas = areas,
                selectedAreaId = selectedAreaId,
                tables = tables,
                products = products,
                onSelectArea = { areaId -> viewModel.selectArea(areaId) },
                onAddTable = { viewModel.addTable() },
                onRemoveTable = { viewModel.removeTable() },
                onSetTableCount = { count -> viewModel.setTableCount(count) },
                onTableClick = { tableId -> navController.navigate("order_screen/$tableId") },
                onCreateArea = { name, prefix -> viewModel.createArea(name, prefix) },
                onCreateProduct = { cat, name, price -> viewModel.createProduct(cat, name, price) },
                onOpenSystemMenu = { navController.navigate("product_management") },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("product_management") {
            val products by viewModel.products.collectAsState()
            ProductManagementScreen(
                products = products,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "order_screen/{tableId}",
            arguments = listOf(navArgument("tableId") { type = NavType.IntType })
        ) { backStackEntry ->
            val tableId = backStackEntry.arguments?.getInt("tableId") ?: 0
            val products by viewModel.products.collectAsState()
            val orderItems by viewModel.getOrderItemsForTable(tableId).collectAsState(initial = emptyList())

            OrderScreen(
                tableId = tableId,
                products = products,
                existingOrderItems = orderItems,
                onSaveOrder = { items, total ->
                    viewModel.saveOrderForTable(tableId, items, total)
                },
                onPayTable = {
                    viewModel.payTable(tableId)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}