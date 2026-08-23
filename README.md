<p align="center">
  <img src="https://media.tenor.com/LuJ6d6nx1nAAAAAi/koseki-bijou-hololive.gif" alt="Demostración POS" width="250"/>
</p>

# 🍽️ RestaurantePOS - Sistema de Punto de Venta para Android
**RestaurantePOS** es una aplicación moderna de punto de venta (POS) para restaurantes desarrollada en Kotlin utilizando **Jetpack Compose** y **Room Database**. Permite la gestión de mesas por áreas, control dinámico de comandas, menú interactivo y generación de reportes diarios de ventas.

---

## 🖥️ Compatibilidad y Dispositivos

La aplicación está optimizada con un diseño responsivo para adaptarse a múltiples factores de forma:

* **Pantallas Táctiles POS (Totems / Monitores Industriales):**
  * Funciona de forma nativa en terminales **POS All-in-One** (Sunmi, Elo Touch, PAX o cualquier hardware industrial con Android).
  * Los botones y tarjetas están escalados para interacción táctil fluida sin necesidad de periféricos externos.
* **Computadoras (Windows / Mac / Linux) y ChromeOS:**
  * Se puede ejecutar en PC a través del emulador de **Android Studio**, **BlueStacks**, o de forma directa en sistemas con **ChromeOS** y computadoras con Android OS / Subsistema Android.
  * El sistema mapea clics de ratón a gestos táctiles de manera transparente.
* **Tablets y Smartphones Android:**
  * Compatible con dispositivos Android 8.0 (API 26) o superior.

---

## 🚀 Características Principales

### 🛋️ Gestión de Mesas y Áreas
* **Organización por Zonas:** Creación y eliminación de áreas de servicio (ej. Terraza, Barra, Salón Principal).
* **Control Visual:** Indicadores en tiempo real sobre el estado de ocupación de las mesas y montos acumulados.
* **Configuración Dinámica:** Posibilidad de modificar la cantidad y numeración de mesas por área.

### 📋 Comandas y Facturación (`OrderScreen`)
* **Toma de Pedidos:** Selección intuitiva de platillos por categorías.
* **Modificación en Tiempo Real:** Incremento/decremento de cantidades de platillos directo en la comanda.
* **Cobro Directo vs. Guardado:** Permite guardar comandas abiertas para cobro posterior o realizar cobros inmediatos directamente desde la mesa.
* **Sincronización Automática:** Estado en tiempo real del carrito sincronizado con la base de datos Room.

### 🍔 Gestión de Menú (`MenuManagementScreen`)
* **Añadir y Editar Productos:** Creación de nuevos platillos con foto, categoría y precio.
* **Visualización Dinámica:** Filtro por categorías y tarjetas interactivas de productos.
* **Almacenamiento Local de Imágenes:** Las fotos subidas desde el dispositivo se almacenan permanentemente en el almacenamiento interno de la app.

### 🔐 Control de Usuarios y Sesiones
* **Roles de Usuario:** Autenticación por PIN de seguridad para Administradores y Meseros.
* **Sesión Activa:** Control de acceso a funciones avanzadas según los permisos del usuario activo.

---

## 🛠️ Tecnologías Utilizadas

* **Lenguaje:** [Kotlin](https://kotlinlang.org/)
* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
* **Navegación:** Jetpack Navigation Compose
* **Arquitectura:** MVVM (Model-View-ViewModel)
* **Base de Datos Local:** [Room Persistence Library](https://developer.android.com/training/data-storage/room)
* **Carga de Imágenes:** [Coil](https://coil-kt.github.io/coil/)
* **Concurrencia:** Kotlin Coroutines & Flow

---

## 📁 Estructura del Proyecto

```text
com.example.restaurantepos/
│
├── data/                       # Capa de Datos (Room DB)
│   ├── AppDatabase.kt          # Instancia principal de SQLite/Room
│   ├── Entities.kt             # Entidades: ProductEntity, OrderItemEntity, etc.
│   ├── PosDao.kt               # Data Access Object para consultas
│   └── OrderDao.kt             # Consultas específicas de comandas y reportes
│
├── ui/                         # Capa de Presentación (Jetpack Compose)
│   ├── MainActivity.kt         # NavHost y gestión de rutas
│   ├── PosViewModel.kt         # Lógica de negocio y estado global
│   ├── OrderScreen.kt          # Interfaz de comanda/factura por mesa
│   ├── MenuManagementScreen.kt # Catálogo y edición del menú
│   ├── TableDashboardScreen.kt # Panel principal de áreas y mesas
│   ├── ProductManagementScreen.kt # Formulario de creación de productos
│   └── UserSelectionScreen.kt  # Pantalla de login/selección de usuario
│
└── utils/                      # Utilidades del sistema
    └── SecurityUtils.kt        # Encriptación / Hash para PINs
```
<p align="center">
  <img src="https://media1.tenor.com/m/qiWJ5Ivkq9IAAAAd/nerissa-cute-dance-rissa-cute-dance.gif" alt="Demostración POS" width="250"/>
</p>


