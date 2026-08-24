<p align="center">
  <img src="https://media.tenor.com/LuJ6d6nx1nAAAAAi/koseki-bijou-hololive.gif" alt="Demostración POS" width="250"/>
</p>

## 👨‍💻 Autoría y Créditos

Este proyecto fue desarrollado de forma independiente por **Sureshum**. Todos los derechos reservados.

* **Desarrollador:** Sureshum
* **Contacto / Soporte:** ssshum25ssshum25@gmail.com
* **Versión:** v1.0.0

---

# 🍽️ RestaurantePOS - Sistema de Punto de Venta para Android

![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Room DB](https://img.shields.io/badge/Room-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)

¡Bienvenido a **RestaurantePOS**! Este sistema permite que los camareros tomen pedidos desde teléfonos o tablets Android y que la caja principal en la computadora los reciba al instante para cobrar e imprimir los recibos.

---

## 📱 ¿Qué incluye este sistema?

1. **Aplicación para Celular/Tablet (Android):** Utilizada por los camareros para seleccionar las mesas, anotar pedidos y enviarlos a la caja.
2. **Programa para la Computadora (Windows):** Ubicado en la caja central. Muestra en pantalla el mapa de mesas en tiempo real (verde si está libre, rojo si está ocupada), cobra en efectivo o tarjeta y genera los recibos en PDF.

---


## 🚀 Guía de Instalación Rápida (Paso a Paso)

No necesitas saber de programación para poner a funcionar el sistema. Sigue estos simples pasos:

### Paso 1: Instalar la App en los Teléfonos de los Camareros
1. Ve a la sección **Releases (Lanzamientos)** a la derecha de esta página en GitHub.
2. Descarga el archivo que termina en **`.apk`**.
3. Pásalo a los teléfonos de los camareros e instálalo (si el teléfono pide permiso para "Instalar aplicaciones desconocidas", presiona **Aceptar**).

### Paso 2: Preparar la Computadora de la Caja
1. En la misma sección de **Releases**, descarga el archivo comprimido **`.zip`**.
2. Descomprime la carpeta en el Escritorio de la computadora.
3. Abre la carpeta y haz doble clic en el programa **`caja_app.exe`**. ¡Listo! Verás la pantalla principal de la caja.

---

## 📦 Compilación y Despliegue (Ejecutable .exe)

El sistema está preparado para empaquetarse en un entorno autónomo de Windows sin requerir instalación previa de Python en la caja central.

### 1. Generar Ejecutable con PyInstaller

Para compilar el proyecto en una carpeta autocontenida con soporte para la generación de archivos PDF y la interfaz en PyQt6, ejecuta el siguiente comando en la terminal:

```powershell
python -m PyInstaller --noconfirm --onedir --windowed --add-data "recibos;recibos" caja_app.py
```
---

## ⚙️ Conectar los Teléfonos con la Computadora

Para que los celulares puedan enviar pedidos a la computadora, **todos los dispositivos deben estar conectados a la misma red Wi-Fi del restaurante**.

1. En la computadora de la caja, averigua su dirección IP local (ejemplo: `192.xxx.x.xx`).
2. abre el terminal y pones este comando
```powershell
ipconfig
```
4. Abre la aplicación en el teléfono del camarero y entra a **Configuración**.
5. Escribe esa misma dirección IP. A partir de ese momento, cada pedido enviado aparecerá automáticamente en la pantalla de la computadora.

---

## ❓ Preguntas Frecuentes

* **¿Qué hago si la computadora no recibe los pedidos?**  
  Asegúrate de que el teléfono no haya perdido la conexión al Wi-Fi del restaurante y que la IP guardada en el teléfono coincida con la de la computadora.
* **¿Dónde se guardan las facturas o recibos?**  
  Dentro de la carpeta del programa en la computadora se crea automáticamente una carpeta llamada `recibos`, donde se guardan todos los archivos PDF con la fecha y hora de cada cobro.

---

## 🖥️ Compatibilidad y Dispositivos

La aplicación está optimizada con un diseño responsivo para adaptarse a múltiples factores de forma:

* **Pantallas Táctiles POS (Totems / Monitores Industriales):** Funciona de forma nativa en terminales POS All-in-One (*Sunmi, Elo Touch, PAX* o hardware industrial Android). Sus botones y tarjetas están dimensionados para interacción táctil fluida.
* **Computadoras (Windows / Mac / Linux) y ChromeOS:** Compatible mediante el emulador de Android Studio, BlueStacks, o de forma directa en ChromeOS y Subsistemas Android (WSA), mapeando clics de ratón a eventos táctiles.
* **Tablets y Smartphones Android:** Compatible con dispositivos con Android 8.0 (API 26) o superior.

---

## 🚀 Características Principales

### 🛋️ Gestión de Mesas y Áreas
* **Organización por Zonas:** Creación, edición y eliminación de áreas de servicio (ej. *Terraza, Barra, Salón Principal*).
* **Control Visual:** Indicadores dinámicos de ocupación y montos acumulados por mesa.
* **Configuración Dinámica:** Modificación instantánea del número y correlativo de mesas por área.

### 📋 Comandas y Facturación (`OrderScreen`)
* **Toma de Pedidos:** Navegación por menú categorizado y adición rápida de ítems al carrito.
* **Modificación en Tiempo Real:** Ajuste fluido de cantidades e ítems directo en la comanda.
* **Cobro Directo vs. Guardado:** Permite mantener comandas abiertas para cobro posterior o efectuar cierres inmediatos de mesa.
* **Sincronización:** Estado del carrito en tiempo real enlazado a la base de datos Room y sincronización mediante polling con servidores remotos de red local.

### 🍔 Gestión de Menú (`MenuManagementScreen`)
* **Añadir y Editar Productos:** Creación de platillos especificando foto, categoría y precio.
* **Almacenamiento Local de Imágenes:** Las imágenes cargadas se persisten de forma permanente en la memoria interna de la aplicación.

### 🔐 Control de Usuarios y Sesiones
* **Roles de Usuario:** Autenticación por PIN de seguridad para Administradores y Meseros.
* **Seguridad:** Restricción de funciones avanzadas del sistema según el tipo de usuario activo.

---

## 📖 Manual de Uso y Flujo de Trabajo

### 1. Inicio de Sesión y Selección de Usuario
1. Abre la aplicación en tu terminal POS, tablet o emulador.
2. En la pantalla principal, selecciona tu usuario (Administrador o Mesero).
3. Ingresa tu **PIN de seguridad** de 4 dígitos para acceder al panel principal.

---

### 2. Configuración Inicial (Administrador)

**Crear Áreas de Servicio:**
1. En el panel superior del dashboard, presiona el icono **➕ (Agregar Sala)**.
2. Ingresa el nombre del área (ej. *Terraza*) y un prefijo corto (ej. *T*).
3. Presiona **Crear**. La nueva pestaña aparecerá en la barra superior.

**Ajustar Cantidad de Mesas:**
1. En la parte inferior del dashboard, presiona **Configurar cantidad de mesas**.
2. Ingresa el número total de mesas requeridas para el área activa y presiona **Guardar**.
*(Nota: La cantidad de mesas se sincronizará automáticamente con la PC si hay conexión activa).*

**Cargar Menú de Productos:**
1. Abre el **Menú del Sistema** (icono de cubiertos en la esquina superior derecha).
2. Selecciona **Añadir Comida al Menú**.
3. Rellena los datos: Categoría, Nombre del platillo, Precio y (opcionalmente) selecciona una imagen desde la galería de tu dispositivo.
4. Presiona **Guardar**.

---

### 3. Flujo Diario de Servicio (Meseros)

**Tomar una Comanda:**
1. En la pantalla de **Mesas**, selecciona una tarjeta de mesa disponible (marcada en color verde).
2. Navega por las categorías mediante el botón flotante inferior o revisa el catálogo general.
3. Toca sobre los productos para agregarlos a la comanda. Cada toque incrementa la cantidad en `x1`.
4. Para revisar la comanda, presiona el botón del **Monto/Factura** en la esquina inferior derecha. Aquí puedes ajustar las cantidades usando los botones `+` y `-`.

**Enviar Comanda a Cocina / Guardar:**
1. En la vista de la comanda, presiona **Enviar Comanda** o toca la flecha de regresar **(←)**.
2. El pedido se guardará localmente en la base de datos de la app y se enviará vía red a la PC.
3. La mesa en el dashboard cambiará automáticamente a estado **Ocupado** (color rojo) mostrando el total acumulado.

---

### 4. Cobro y Liberación de Mesas

1. Toca una mesa ocupada para abrir su comanda activa.
2. Abre la vista de factura presionando el botón del total acumulado.
3. Presiona el botón **Pagar Mesa**.
4. La aplicación realizará automáticamente las siguientes acciones:
   * Generará un **recibo en formato PDF**.
   * Enviará la notificación de cierre a la PC servidor vía IP.
   * Vaciará el pedido en la base de datos local y cambiará el estado de la mesa a **Disponible** (verde).

---

### 5. Cierre de Día y Reportes (Administrador)

1. En la barra superior del dashboard, presiona el icono de **Reportes / Cierre de Día (📊)**.
2. Se desplegará un resumen interactivo con:
   * Total de productos vendidos en el turno.
   * Ventas totales acumuladas en dinero.
3. Presiona **Confirmar Cierre de Día** para generar el reporte general, limpiar las ventas pendientes de consolidar y exportar el informe para administración.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Navegación** | Jetpack Navigation Compose |
| **Arquitectura** | MVVM (Model-View-ViewModel) |
| **Base de Datos** | Room Persistence Library |
| **Carga de Imágenes** | Coil |
| **Concurrencia** | Kotlin Coroutines & Flow |

---

## 📁 Estructura del Proyecto

```text
com.example.restaurantepos/
│
├── data/                       # Capa de Datos (Room DB & Modelos)
│   ├── AppDatabase.kt          # Instancia principal de SQLite/Room
│   ├── Entities.kt             # Entidades: ProductEntity, OrderItemEntity, etc.
│   ├── PosDao.kt               # Data Access Object para consultas generales
│   └── OrderDao.kt             # Consultas de comandas y reportes
│
├── ui/                         # Capa de Presentación (Jetpack Compose)
│   ├── MainActivity.kt         # NavHost y gestión de rutas
│   ├── PosViewModel.kt         # Lógica de negocio y estado global
│   ├── OrderScreen.kt          # Interfaz de comanda y cobranza por mesa
│   ├── MenuManagementScreen.kt # Catálogo y edición del menú
│   ├── TableDashboardScreen.kt # Panel principal de áreas, mesas y polling
│   ├── ProductManagementScreen.kt # Formulario de registro de productos
│   └── UserSelectionScreen.kt  # Pantalla de inicio de sesión por PIN
│
└── utils/                      # Utilidades y Red
    ├── ExportManager.kt        # Generación de PDF y peticiones HTTP local
    └── SecurityUtils.kt        # Funciones de hash y seguridad para PINs
```
<p align="center">
  <img src="https://media1.tenor.com/m/qiWJ5Ivkq9IAAAAd/nerissa-cute-dance-rissa-cute-dance.gif" alt="Demostración POS" width="250"/>
</p>


