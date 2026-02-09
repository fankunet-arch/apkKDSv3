package com.toptea.topteakds

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset

data class PrintPayload(
    val size: String,
    val commands: List<PrintCommand>
)

data class PrintCommand(
    val type: String,
    val value: String? = null,
    val key: String? = null,
    val char: String? = "-",
    val lines: Int = 1
)

object PrinterService {
    private val gson = Gson()

    suspend fun printJob(config: PrinterConfig, payloadJson: String) = withContext(Dispatchers.IO) {
        if (config.type != "WIFI") {
            throw Exception("Only WIFI printing is supported currently.")
        }
        if (config.ip.isEmpty()) {
            throw Exception("Printer IP not configured.")
        }

        val payload = try {
            gson.fromJson(payloadJson, PrintPayload::class.java)
        } catch (e: Exception) {
            throw Exception("Invalid print payload JSON: ${e.message}")
        }

        val isTspl = payload.size.lowercase().contains("x")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(config.ip, config.port), 5000) // 5s timeout
            val outputStream = socket.getOutputStream()

            if (isTspl) {
                printTspl(outputStream, payload)
            } else {
                printEscPos(outputStream, payload)
            }
            outputStream.flush()
        } catch (e: Exception) {
            throw Exception("Print failed: ${e.message}")
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun printEscPos(os: OutputStream, payload: PrintPayload) {
        val widthChars = if (payload.size.contains("58")) 32 else 48
        val gbk = try {
            Charset.forName("GBK")
        } catch (e: Exception) {
            Charset.defaultCharset()
        }

        // Init
        os.write(byteArrayOf(0x1B, 0x40)) // ESC @

        for (cmd in payload.commands) {
            when (cmd.type) {
                "text" -> {
                    cmd.value?.let {
                        os.write(it.toByteArray(gbk))
                        os.write(byteArrayOf(0x0A)) // LF
                    }
                }
                "divider" -> {
                    val char = cmd.char ?: "-"
                    if (char.isNotEmpty()) {
                        val repeatCount = (widthChars / char.length).coerceAtLeast(1)
                        val line = char.repeat(repeatCount)
                        os.write(line.toByteArray(gbk))
                        os.write(byteArrayOf(0x0A))
                    }
                }
                "kv" -> {
                    val key = cmd.key ?: ""
                    val value = cmd.value ?: ""
                    val line = "$key: $value"
                    os.write(line.toByteArray(gbk))
                    os.write(byteArrayOf(0x0A))
                }
                "feed" -> {
                    val lines = cmd.lines
                    repeat(lines) {
                        os.write(byteArrayOf(0x0A))
                    }
                }
                "cut" -> {
                    // GS V 66 0
                    os.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
                }
            }
        }
        // Ensure final feed if not cut
        if (payload.commands.none { it.type == "cut" }) {
             os.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        }
    }

    private fun printTspl(os: OutputStream, payload: PrintPayload) {
        // Parse size "50x30 mm" -> 50, 30
        val sizeClean = payload.size.replace("mm", "", ignoreCase = true).trim()
        val sizeParts = sizeClean.split("x")
        val width = if (sizeParts.isNotEmpty()) sizeParts[0].trim() else "40"
        val height = if (sizeParts.size > 1) sizeParts[1].trim() else "30"

        val gbk = try {
            Charset.forName("GBK")
        } catch (e: Exception) {
            Charset.defaultCharset()
        }

        // Setup
        writeTspl(os, "SIZE $width mm, $height mm", gbk)
        writeTspl(os, "GAP 2 mm, 0 mm", gbk)
        writeTspl(os, "CLS", gbk)

        var y = 10
        val lineHeight = 40

        for (cmd in payload.commands) {
            when (cmd.type) {
                "text" -> {
                    cmd.value?.let {
                        // TEXT x,y,"font",rotation,x-mul,y-mul,"content"
                        val textCmd = "TEXT 10,$y,\"TSS24.BF2\",0,1,1,\"$it\""
                        writeTspl(os, textCmd, gbk)
                        y += lineHeight
                    }
                }
                "kv" -> {
                    val line = "${cmd.key}: ${cmd.value}"
                    val textCmd = "TEXT 10,$y,\"TSS24.BF2\",0,1,1,\"$line\""
                    writeTspl(os, textCmd, gbk)
                    y += lineHeight
                }
            }
        }

        writeTspl(os, "PRINT 1,1", gbk)
    }

    private fun writeTspl(os: OutputStream, cmd: String, charset: Charset) {
        os.write(cmd.toByteArray(charset))
        os.write(byteArrayOf(0x0A))
    }
}
