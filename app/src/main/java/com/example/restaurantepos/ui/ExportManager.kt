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
        waiterName: String = "Camarero"
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
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Mesa: Mesa $tableId", 60f, y, paint)
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
        val items: List<ItemOrderRequest>
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
    fun sendPdfToPc(pdfFile: File, pcIpAddress: String, tableId: Int? = null, port: Int = 5000) {
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
                    if (tableId != null) {
                        sendOrderJsonToPc(
                            pcIpAddress = pcIpAddress,
                            tableName = "Mesa $tableId",
                            waiterName = "",
                            itemsList = emptyList(),
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

    // Consulta el estado de las mesas a la PC
    fun fetchTablesFromPc(
        pcIpAddress: String,
        port: Int = 5000,
        onSuccess: (JSONObject) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$pcIpAddress:$port/tables")
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

}