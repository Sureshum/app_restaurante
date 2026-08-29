package com.example.restaurantepos.ui

import android.net.Uri
import com.example.restaurantepos.BuildConfig
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.restaurantepos.data.UserEntity
import com.example.restaurantepos.data.UserRole

@Composable
fun UserSelectionScreen(
    users: List<UserEntity>,
    onAuthenticate: (UserEntity, String, () -> Unit, () -> Unit) -> Unit,
    onCreateUser: (String, String, String, UserRole) -> Unit,
    onDeleteUser: (UserEntity, String, () -> Unit, () -> Unit) -> Unit
) {
    var selectedUser by remember { mutableStateOf<UserEntity?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Añadir Usuario") },
                text = { Text("Nuevo Usuario") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header con botón de Configuración IP a la derecha
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column {
                    Text(
                        text = "Seleccionar Usuario",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "restaurantSSS Móvil • v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                    Row {
                        IconButton(onClick = { showIpDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurar IP PC",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(users, key = { it.id }) { user ->
                        Card(
                            modifier = Modifier
                                .height(140.dp)
                                .clickable { selectedUser = user },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                AsyncImage(
                                    model = user.avatarUri.ifEmpty { "https://via.placeholder.com/150" },
                                    contentDescription = user.name,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = user.role.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            selectedUser?.let { user ->
                AlertDialog(
                    onDismissRequest = {
                        selectedUser = null
                        enteredPin = ""
                    },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PIN de ${user.name}", fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    onDeleteUser(
                                        user,
                                        enteredPin,
                                        {
                                            Toast.makeText(context, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                                            selectedUser = null
                                            enteredPin = ""
                                        },
                                        {
                                            Toast.makeText(context, "PIN incorrecto o sin privilegios", Toast.LENGTH_SHORT).show()
                                            enteredPin = ""
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar usuario",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = enteredPin.padStart(4, '•'),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(20.dp))

                            val buttons = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "CLR", "0", "OK")
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.width(240.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(buttons) { btn ->
                                    Button(
                                        onClick = {
                                            when (btn) {
                                                "CLR" -> enteredPin = ""
                                                "OK" -> {
                                                    onAuthenticate(
                                                        user,
                                                        enteredPin,
                                                        {
                                                            selectedUser = null
                                                            enteredPin = ""
                                                        },
                                                        {
                                                            Toast.makeText(context, "PIN Incorrecto", Toast.LENGTH_SHORT).show()
                                                            enteredPin = ""
                                                        }
                                                    )
                                                }
                                                else -> if (enteredPin.length < 4) enteredPin += btn
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (btn == "OK") MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (btn == "OK") MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Text(btn, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }

            if (showCreateDialog) {
                CreateUserDialog(
                    onDismiss = { showCreateDialog = false },
                    onUserCreated = onCreateUser
                )
            }

            if (showIpDialog) {
                ConfigIpDialog(
                    onDismiss = { showIpDialog = false }
                )
            }

        }
    }
}

@Composable
fun CreateUserDialog(onDismiss: () -> Unit, onUserCreated: (String, String, String, UserRole) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.WAITER) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            imageUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Usuario", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN (4 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(selected = role == UserRole.WAITER, onClick = { role = UserRole.WAITER })
                    Text("Camarero")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN })
                    Text("Admin")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && pin.length == 4) {
                        onUserCreated(name, pin, imageUri?.toString() ?: "", role)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}