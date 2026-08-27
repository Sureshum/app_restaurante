package com.example.restaurantepos.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.restaurantepos.data.ProductEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    // Genera el ticket de cobro en PDF (idéntico al de la PC)
    fun generatePdfReceipt(
        context: Context,
        tableId: Int,
        items: List<Pair<ProductEntity, Int>>,
        totalAmount: Double,
        cashAmount: Double = 0.0,
        cardAmount: Double = totalAmount,
        waiterName: String = "Camarero",
        tableNameDisplay: String? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // Tamaño Carta (Letter)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint()
        val boldPaint = Paint().apply { isFakeBoldText = true }

        var y = 60f

        // Título
        boldPaint.textSize = 18f
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("--- RESTAURANTE POS ---", 306f, y, boldPaint)
        y += 35f

        // Encabezado Mesa y Camarero
        val mesaText = tableNameDisplay ?: "Mesa $tableId"
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Mesa: $mesaText", 60f, y, paint)
        canvas.drawText("Atendió: $waiterName", 300f, y, paint)
        y += 20f

        // Fecha
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentDate = sdf.format(Date())
        canvas.drawText("Fecha: $currentDate", 60f, y, paint)
        y += 35f

        // Cabecera de la Tabla
        boldPaint.textSize = 12f
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Producto", 60f, y, boldPaint)
        canvas.drawText("Cant.", 300f, y, boldPaint)
        canvas.drawText("Precio", 400f, y, boldPaint)
        canvas.drawText("Subtotal", 480f, y, boldPaint)
        y += 15f

        // Línea divisoria
        canvas.drawLine(60f, y, 552f, y, paint)
        y += 20f

        // Filas de productos
        paint.textSize = 11f
        for ((product, qty) in items) {
            val subtotal = product.price * qty
            canvas.drawText(product.name, 60f, y, paint)
            canvas.drawText(qty.toString(), 300f, y, paint)
            canvas.drawText(String.format(Locale.US, "$%.2f", product.price), 400f, y, paint)
            canvas.drawText(String.format(Locale.US, "$%.2f", subtotal), 480f, y, paint)
            y += 20f
        }

        // Línea divisoria inferior
        canvas.drawLine(60f, y, 552f, y, paint)
        y += 25f

        // Totales y Métodos de Pago
        boldPaint.textSize = 14f
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format(Locale.US, "TOTAL: $%.2f", totalAmount), 350f, y, boldPaint)
        y += 20f

        val changeAmount = (cashAmount + cardAmount) - totalAmount
        paint.textSize = 11f
        canvas.drawText(String.format(Locale.US, "Efectivo: $%.2f", cashAmount), 350f, y, paint)
        y += 15f
        canvas.drawText(String.format(Locale.US, "Tarjeta: $%.2f", cardAmount), 350f, y, paint)
        y += 15f
        canvas.drawText(String.format(Locale.US, "Cambio: $%.2f", if (changeAmount > 0) changeAmount else 0.0), 350f, y, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "Recibo_Mesa_$tableId.pdf")
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    data class ItemOrderRequest(
        val nombre: String,
        val cantidad: Int,
        val precio: Double
    )

    data class OrderPayloadRequest(
        val mesa: String,
        val camarero: String,
        val items: List<ItemOrderRequest>,
        val areaId: Int? = null
    )

    // Genera el archivo CSV para Excel
    fun generateExcelCsvReport(
        context: Context,
        tableId: Int,
        items: List<Pair<ProductEntity, Int>>,
        total: Double
    ): File {
        val csvFile = File(context.getExternalFilesDir(null), "Ventas_Mesa_${tableId}.csv")
        val writer = csvFile.bufferedWriter()

        writer.write("Mesa,Producto,Precio Unitario,Cantidad,Subtotal\n")

        items.forEach { (product, qty) ->
            val subtotal = product.price * qty
            writer.write("${tableId},\"${product.name}\",${product.price},${qty},${subtotal}\n")
        }

        writer.write(",,,TOTAL,${total}\n")
        writer.flush()
        writer.close()

        return csvFile
    }

    // Invoca el menú de Android para compartir/enviar
    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar reporte o recibo..."))
    }

    // Envía el PDF automáticamente por red local Wi-Fi a la PC y notifica que se libere la mesa
    fun sendPdfToPc(
        pdfFile: File,
        pcIpAddress: String,
        tableId: Int? = null,
        tableName: String? = null,
        areaId: Int? = null,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val boundary = "*****" + System.currentTimeMillis() + "*****"
                val url = URL("http://$pcIpAddress:$port/upload-pdf")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    useCaches = false
                    setRequestProperty("Connection", "Keep-Alive")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                val outputStream: OutputStream = connection.outputStream
                val writer = outputStream.bufferedWriter()

                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${pdfFile.name}\"\r\n")
                writer.write("Content-Type: application/pdf\r\n\r\n")
                writer.flush()

                // Transmitir bytes del archivo
                val inputStream = FileInputStream(pdfFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                inputStream.close()

                writer.write("\r\n--$boundary--\r\n")
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("PDF enviado exitosamente a la PC")

                    // Si se especificó la mesa, enviamos una orden vacía para que se ponga verde (disponible) en la PC
                    val finalTableName = tableName ?: if (tableId != null) "Mesa $tableId" else null
                    if (finalTableName != null) {
                        sendOrderJsonToPc(
                            pcIpAddress = pcIpAddress,
                            tableName = finalTableName,
                            waiterName = "",
                            itemsList = emptyList(),
                            areaId = areaId,
                            port = port
                        )
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Genera el reporte CSV de Cierre de Día
    fun generateDailySummaryCsv(
        context: Context,
        pendingItems: List<com.example.restaurantepos.data.OrderItemEntity>,
        totalRevenue: Double
    ): File {
        val csvFile = File(context.getExternalFilesDir(null), "Cierre_Caja_${System.currentTimeMillis()}.csv")
        val writer = csvFile.bufferedWriter()

        writer.write("Producto,Cantidad Vendida,Precio Unitario,Subtotal\n")

        val groupedItems = pendingItems.groupBy { it.productName }

        groupedItems.forEach { (productName, items) ->
            val quantity = items.size
            val unitPrice = items.firstOrNull()?.price ?: 0.0
            val subtotal = quantity * unitPrice
            writer.write("\"$productName\",$quantity,$unitPrice,$subtotal\n")
        }

        writer.write("\n")
        writer.write("TOTAL PRODUCTOS,${pendingItems.size},,\n")
        writer.write("TOTAL GANANCIAS,,,$totalRevenue\n")

        writer.flush()
        writer.close()

        return csvFile
    }

    // Guardar IP en el teléfono
    fun savePcIp(context: Context, ip: String) {
        val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pc_ip", ip.trim()).apply()
    }

    fun getPcIp(context: Context): String {
        val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pc_ip", "192.168.x.xx") ?: "192.168.x.xx"
    }

    // Envía la orden estructurada en JSON a la App de PC
    fun sendOrderJsonToPc(
        pcIpAddress: String,
        tableName: String,
        waiterName: String,
        itemsList: List<ItemOrderRequest>,
        areaId: Int? = null,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/order")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val jsonItems = JSONArray()
                itemsList.forEach { item ->
                    val jsonItem = JSONObject().apply {
                        put("nombre", item.nombre)
                        put("cantidad", item.cantidad)
                        put("precio", item.precio)
                    }
                    jsonItems.put(jsonItem)
                }

                val payload = JSONObject().apply {
                    put("mesa", tableName)
                    put("camarero", waiterName)
                    put("items", jsonItems)
                    if (areaId != null) {
                        put("areaId", areaId)
                    }
                }

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("Comanda o actualización de mesa enviada a la caja")
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Registra una venta realizada desde el teléfono para que aparezca
    // en el reporte de Cierre de Día de la PC (además de liberar la mesa)
    fun registerSaleOnPc(
        pcIpAddress: String,
        mesa: String,
        sala: String,
        camarero: String,
        itemsList: List<ItemOrderRequest>,
        efectivo: Double,
        tarjeta: Double,
        areaId: Int? = null,
        port: Int = 5000,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var ok = false
            try {
                val url = URL("http://$pcIpAddress:$port/sale")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val jsonItems = JSONArray()
                itemsList.forEach { item ->
                    val jsonItem = JSONObject().apply {
                        put("nombre", item.nombre)
                        put("cantidad", item.cantidad)
                        put("precio", item.precio)
                    }
                    jsonItems.put(jsonItem)
                }

                val payload = JSONObject().apply {
                    put("mesa", mesa)
                    put("sala", sala)
                    put("camarero", camarero)
                    put("items", jsonItems)
                    put("efectivo", efectivo)
                    put("tarjeta", tarjeta)
                    if (areaId != null) {
                        put("areaId", areaId)
                    }
                }

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    println("Venta registrada en la caja")
                    ok = true
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                onResult?.invoke(ok)
            }
        }
    }

    // Notifica a la PC que se finalizó el día desde el teléfono (limpia sus ventas)
    fun registerDayResetOnPc(
        pcIpAddress: String,
        port: Int = 5000,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var ok = false
            try {
                val url = URL("http://$pcIpAddress:$port/reset-day")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    println("Día finalizado en la PC")
                    ok = true
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                onResult?.invoke(ok)
            }
        }
    }

    data class SyncFastResponse(
        val version: Int,
        val hasChanged: Boolean,
        val areas: JSONArray?,
        val products: JSONArray?,
        val tables: JSONObject,
        val dailySales: JSONArray?,
        val dayReset: Double
    )

    // Sincronización ultrarrápida combinada (Salas, Productos y Mesas en una sola petición)
    fun fetchFastSync(
        pcIpAddress: String,
        areaId: Int? = null,
        currentVersion: Int = 0,
        port: Int = 5000,
        onSuccess: (SyncFastResponse) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlStr = if (areaId != null) {
                    "http://$pcIpAddress:$port/sync-fast?areaId=$areaId&version=$currentVersion"
                } else {
                    "http://$pcIpAddress:$port/sync-fast?version=$currentVersion"
                }
                val url = URL(urlStr)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = connection.inputStream.bufferedReader().use { it.readText() }
                    val rootObj = JSONObject(stream)
                    val version = rootObj.optInt("version", 0)
                    val hasChanged = rootObj.optBoolean("has_changed", false)
                    val areasArray = if (hasChanged) rootObj.optJSONArray("areas") else null
                    val productsArray = if (hasChanged) rootObj.optJSONArray("products") else null
                    val tablesObj = rootObj.optJSONObject("tables") ?: JSONObject()
                    val dailySalesObj = rootObj.optJSONArray("daily_sales")
                    val dayReset = rootObj.optDouble("day_reset", 0.0)

                    val result = SyncFastResponse(
                        version = version,
                        hasChanged = hasChanged,
                        areas = areasArray,
                        products = productsArray,
                        tables = tablesObj,
                        dailySales = dailySalesObj,
                        dayReset = dayReset
                    )

                    withContext(Dispatchers.Main) {
                        onSuccess(result)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Silencioso ante pérdidas momentáneas de red
            }
        }
    }

    // Consulta el estado de las mesas a la PC (opcionalmente filtrado por sala)
    fun fetchTablesFromPc(
        pcIpAddress: String,
        areaId: Int? = null,
        port: Int = 5000,
        onSuccess: (JSONObject) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlStr = if (areaId != null) {
                    "http://$pcIpAddress:$port/tables?areaId=$areaId"
                } else {
                    "http://$pcIpAddress:$port/tables"
                }
                val url = URL(urlStr)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(stream)

                    withContext(Dispatchers.Main) {
                        onSuccess(jsonResponse)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Notifica a la PC la nueva cantidad total de mesas configurada en el teléfono
    fun updateTableCountOnPc(
        pcIpAddress: String,
        newCount: Int,
        areaId: Int? = null,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/set-tables-count")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val payload = JSONObject().apply {
                    put("count", newCount)
                    if (areaId != null) {
                        put("areaId", areaId)
                    }
                }

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("Cantidad de mesas actualizada en la PC exitosamente")
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- SINCRONIZACIÓN DE SALAS / ÁREAS ---

    fun fetchAreasFromPc(
        pcIpAddress: String,
        port: Int = 5000,
        onSuccess: (JSONArray) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/areas")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(stream)

                    withContext(Dispatchers.Main) {
                        onSuccess(jsonArray)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createAreaOnPc(
        pcIpAddress: String,
        name: String,
        prefix: String,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/areas")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                val payload = JSONObject().apply {
                    put("name", name)
                    put("prefix", prefix)
                }

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteAreaOnPc(
        pcIpAddress: String,
        areaId: Int,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/areas/$areaId")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- SINCRONIZACIÓN DE MENÚ Y PRODUCTOS ---

    fun fetchProductsFromPc(
        pcIpAddress: String,
        port: Int = 5000,
        onSuccess: (JSONArray) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/products")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(stream)

                    withContext(Dispatchers.Main) {
                        onSuccess(jsonArray)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createProductOnPc(
        pcIpAddress: String,
        category: String,
        name: String,
        price: Double,
        imageUri: String? = null,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/products")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                val payload = JSONObject().apply {
                    put("category", category)
                    put("name", name)
                    put("price", price)
                    if (imageUri != null) put("imageUri", imageUri)
                }

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateProductOnPc(
        pcIpAddress: String,
        productId: Int,
        category: String,
        name: String,
        price: Double,
        imageUri: String? = null,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/products/$productId")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                val payload = JSONObject().apply {
                    put("id", productId)
                    put("category", category)
                    put("name", name)
                    put("price", price)
                    if (imageUri != null) put("imageUri", imageUri)
                }

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteProductOnPc(
        pcIpAddress: String,
        productId: Int,
        port: Int = 5000
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/products/$productId")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun testConnection(
        pcIpAddress: String,
        port: Int = 5000,
        onResult: (Boolean, String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanIp = pcIpAddress.trim()
                if (cleanIp.isBlank() || cleanIp == "192.168.x.xx") {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Ingresa una IP válida antes de probar.")
                    }
                    return@launch
                }

                val url = URL("http://$cleanIp:$port/ping")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                val code = connection.responseCode
                connection.disconnect()

                withContext(Dispatchers.Main) {
                    if (code == HttpURLConnection.HTTP_OK) {
                        onResult(true, "✅ ¡Conexión Exitosa con el Servidor Madre!")
                    } else {
                        onResult(false, "❌ El servidor respondió con código $code.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "❌ No se pudo conectar. Verifica la IP y que la PC tenga el servidor encendido.")
                }
            }
        }
    }
}