<p align="center">
  <img src="https://media.tenor.com/LuJ6d6nx1nAAAAAi/koseki-bijou-hololive.gif" alt="Demostración POS" width="250"/>
</p>

## 👨‍💻 Autoría y Créditos

Este proyecto fue desarrollado de forma independiente por **Sureshum**.

* **Desarrollador:** Sureshum
* **Contacto / Soporte:** ssshum25ssshum25@gmail.com
* **Versión:** v1.8.2

---

# 🍽️ RestaurantePOS - Sistema de Punto de Venta (Android + Windows)

![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.10%2B-3776AB?style=for-the-badge&logo=python&logoColor=white)
![PyQt6](https://img.shields.io/badge/PyQt6-GUI-41CD52?style=for-the-badge&logo=qt&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-Server-009688?style=for-the-badge&logo=fastapi&logoColor=white)

¡Bienvenido a **RestaurantSSS**! Este sistema cliente-servidor permite que los camareros tomen pedidos desde dispositivos Android y que la caja central en una computadora reciba las comandas al instante, administre las mesas y emita facturas en PDF.

---

## 📱 ¿Cómo está estructurado el sistema?

El proyecto consta de dos partes principales que se comunican en red local:

**📱 App Móvil (Android - Cliente):** Utilizada por los camareros para seleccionar las mesas, navegar el menú, tomar comandas y enviarlas en tiempo real.

**💻 Servidor y Central de Caja (Windows - PC):** Instalado en la computadora de la caja. Incluye una interfaz de control visual (PyQt6) y un servidor HTTP (FastAPI) que recibe los pedidos, 
actualiza el mapa de mesas en tiempo real y genera/guarda los recibos en formato PDF.

---

## 🚀 Guía de Instalación Rápida (Paso a Paso)

### Paso 1: Instalar la App Móvil en los Teléfonos (Android)
* Ve a la sección **Releases (Lanzamientos)** a la derecha de esta página en GitHub.
* Descarga el archivo ejecutable móvil **`.apk`**.
* Transfiérelo e instálalo en los teléfonos de los camareros (si el sistema solicita permiso para "Instalar aplicaciones desconocidas", presiona **Aceptar**).

### Paso 2: Ejecutar el Servidor de Caja en la Computadora (Windows)
* En la misma sección de **Releases**, descarga el archivo comprimido del servidor **`.zip`**.
* Descomprime la carpeta en la computadora de la caja central.
* Abre la carpeta descomprimida y ejecuta **`caja_app.exe`**. Verás la interfaz gráfica del mapa de mesas y el servidor de red se iniciará automáticamente.

---

## 📦 Compilación del Ejecutable de PC (`caja_app.exe`)

Si deseas modificar el código fuente del servidor de caja (`caja_app.py`) y compilar tu propio ejecutable para Windows sin requerir Python en la PC de destino, utiliza PyInstaller:

```powershell
python -m PyInstaller --noconfirm --onedir --windowed --collect-all uvicorn --collect-all fastapi --hidden-import="uvicorn.logging" --hidden-import="uvicorn.loops" --hidden-

import="uvicorn.loops.auto" --hidden-import="uvicorn.protocols" --hidden-import="uvicorn.protocols.http" --hidden-import="uvicorn.protocols.http.h11_impl" --hidden-import="uvicorn.lifespan" --hidden-import="uvicorn.lifespan.on" --name "RestaurantePOS" caja_app.py
```

⚙️ Conectar los Teléfonos con la Computadora
---
Para que los celulares puedan enviar los pedidos a la caja, todos los dispositivos deben estar conectados a la misma red Wi-Fi del local.

Abre la aplicación de la caja en la computadora; en la barra superior se mostrará la dirección IP asignada a la PC (ejemplo: 192.xxx.x.xx).Si prefieres verificarla manualmente en Windows, 

abre la consola (CMD/PowerShell) y ejecuta:
```PowerShell
ipconfig
```
---
Copia la dirección de la IPv4.En el teléfono del camarero, abre la aplicación Android, ingresa a Configuración y guarda esa misma dirección IP.
A partir de ese momento, cada pedido enviado se sincronizará automáticamente con la PC.


## ❓ Preguntas Frecuentes
¿Qué hago si la computadora no recibe los pedidos de los celulares? 

* Asegúrate de que el teléfono esté conectado a la misma red Wi-Fi y de que el Firewall de Windows permita el tráfico por el puerto 5000 en la PC.

¿Dónde se guardan las facturas y recibos generados? 

* Dentro de la carpeta del programa en la computadora se crea automáticamente una carpeta llamada recibos, donde se almacenan los archivos PDF firmados con la fecha y hora de cada transacción.

---
## 🖥️ Compatibilidad y Dispositivos

* **Central de Caja (Servidor):** Windows 10 / 11 (ejecutable `.exe` autónomo).
* **Terminales de Camarero (Cliente):** Smartphones, Tablets y Puntos de Venta Android (*Sunmi, Elo Touch, PAX*) con **Android 8.0 (API 26)** o superior.

---

## 🚀 Características Principales

### 🛋️ Gestión de Mesas y Áreas
* **Organización por Zonas:** Creación, edición y eliminación de áreas de servicio (ej. *Terraza, Barra, Salón Principal*).
* **Control Visual:** Indicadores dinámicos de ocupación (verde = libre, rojo = ocupado) y montos acumulados por mesa.

### 📋 Comandas y Facturación (`OrderScreen`)
* **Toma de Pedidos:** Navegación por menú categorizado y adición rápida de ítems al carrito.
* **Modificación en Tiempo Real:** Ajuste fluido de cantidades e ítems directo en la comanda.
* **Sincronización:** Estado del carrito sincronizado localmente y enviado al servidor FastAPI de la caja.

### 🍔 Gestión de Menú (`MenuManagementScreen`)
* **Añadir y Editar Productos:** Creación de platillos especificando foto, categoría y precio.
* **Almacenamiento de Imágenes:** Persistencia de imágenes del menú tanto en la app Android como en el backend de la PC.

### 🌎 Configuración Multipaís (POS Restaurante)
* **Selector de País (solo PC):** Elige el país del negocio desde el botón `🌎 País` en la caja (PC). Es la única fuente de verdad: la elección se guarda en el servidor y se propaga automáticamente a todos los teléfonos.
* **Sincronización automática:** El teléfono descarga la configuración del país durante cada sincronización (`/sync-fast`) y muestra la moneda e impuesto correctos sin necesidad de configurarlo en cada terminal.
* **Impuesto y moneda dinámicos:** Cada país define su impuesto (IVA/ITBMS/ISV/IGV...), su tasa y su símbolo de moneda (MXN, GTQ, USD, EUR...). Al cambiar de país en la PC se actualiza automáticamente el porcentaje.
* **Esquema fiscal por región:** CFDI (MX), FEL (GT), CCF (SV), DTE (CL), VERI*FACTU (ES), etc. Se muestra en el ticket/recibo y se almacena en cada venta.
* **Personalizado:** Opción "Personalizado" para negocios con impuesto/divisa propios.

### 🖨️ Impresión de Tickets Térmicos (PC)
* **Impresión al cobrar:** Genera e imprime ticket térmico en formato POS restaurante (80mm y 58mm).
* **Impresoras:** Compatible con impresoras térmicas Windows mediante `win32print` (driver Genérico / Solo texto); fallback a archivo `.txt` que se abre para imprimir.
* **Configurable:** Activar/desactivar impresión y elegir ancho de impresora desde `🌎 País`.

### 💳 Cobro con Tarjeta (TPV / Terminal bancaria)
* **Datos de la terminal:** Ingresa marca, últimos 4 dígitos, código de aprobación y referencia de la transacción al cobrar.
* **Cobro NFC/TAP (contactless):** Marca la opción **NFC/TAP** para registrar pagos sin contacto; el ticket lo indica como `NFC/TAP`.
* **Ticket y recibo:** La información del pago con tarjeta aparece en el ticket térmico y en el recibo PDF.
* **Identificación fiscal:** Campo opcional de NIT/CUI/RUT/cédula del cliente según el país, incluido en el ticket.
* **Métodos mixtos:** Divide el pago entre efectivo y tarjeta con cálculo de cambio/vuelto.

### 📊 Exportación a Excel / Contabilidad
* **Botón "Exportar Excel / Contabilidad"** en el Cierre de Día: genera un archivo CSV compatible con Excel (con BOM UTF-8 para acentos y símbolos de moneda).
* **Datos por línea de venta:** fecha, mesa, sala, camarero, producto, cantidad, precio, subtotal, impuesto, total, moneda, efectivo, tarjeta, cambio, marca de tarjeta, terminal (últ 4), aprobación, referencia, esquema fiscal e identificación fiscal.
* **Resumen del día:** subtotal, impuesto, total vendido, efectivo, tarjeta y esquema fiscal.
* **Multimoneda:** Usa la moneda e impuesto del país activo, así que el archivo es válido para tu contador en cualquier región.
### 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Cliente Móvil (Android)** | Kotlin, Jetpack Compose, Room DB, Coroutines, Coil |
| **Servidor de Caja (PC)** | Python 3.14, FastAPI, Uvicorn, PyQt6, ReportLab (PDF) |
| **Arquitectura de Red** | REST API (HTTP / JSON en Red Local) |
| **Empaquetado PC** | PyInstaller |


## 📁 Estructura del Proyecto

```text
app_restaurante/
├── app/                           # Módulo Cliente Android
│   └── src/main/java/com/example/restaurante/
│       ├── data/                  # Persistencia local y modelos (Room DB)
│       │   ├── AppDatabase.kt     # Base de datos SQLite / Room
│       │   ├── Entities.kt        # Modelos de datos del sistema
│       │   ├── PosDao.kt          # DAO para operaciones generales del POS
│       │   ├── OrderDao.kt        # DAO para comandas y reportes
│       │   └── SecurityUtils.kt   # Seguridad y cifrado/hashing de PINs
│       │
│       ├── ui/                    # Interfaz de usuario (Jetpack Compose)
│       │   ├── MainActivity.kt            # Punto de entrada y navegación
│       │   ├── PosViewModel.kt            # Estado global y lógica de negocio
│       │   ├── TableDashboardScreen.kt   # Vista principal y control de mesas
│       │   ├── OrderScreen.kt            # Gestión de comandas por mesa
│       │   ├── MenuManagementScreen.kt    # Edición y administración del menú
│       │   ├── ProductManagementScreen.kt # Registro de productos
│       │   ├── UserSelectionScreen.kt     # Autenticación de usuarios por PIN
│       │   ├── UserSession.kt             # Control de sesión activa
│       │   ├── DailyReportEntity.kt       # Manejo de datos de cierre diario
│       │   ├── ExportManager.kt          # Cliente HTTP para envío a la PC
│       │   ├── ConfigIpDialog.kt          # Diálogo de configuración IP del servidor
│       │   └── EndDayDialog.kt            # Diálogo de confirmación de cierre diario
│       │
│       └── AndroidManifest.xml    # Permisos de red y configuración del sistema
│
├── caja_app.py                    # Servidor FastAPI + Interfaz PyQt6 (PC Windows)
├── recibos/                       # Almacenamiento local de facturas generadas en PDF
└── imagenes_productos/            # Directorio de imágenes persistidas del catálogo
```
<p align="center">
  <img src="https://media1.tenor.com/m/qiWJ5Ivkq9IAAAAd/nerissa-cute-dance-rissa-cute-dance.gif" alt="Demostración POS" width="250"/>
</p>


