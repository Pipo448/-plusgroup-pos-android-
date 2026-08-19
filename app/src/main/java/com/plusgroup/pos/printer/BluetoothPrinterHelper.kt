package com.plusgroup.pos.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.OutputStream
import java.util.UUID

/**
 * Enprimant tèmik EKSTÈN ki konekte pa Bluetooth, itilize pwotokòl ESC/POS
 * estanda. Sa mache ak PRESKE TOUT enprimant tèmik Bluetooth ki egziste
 * (san bezwen SDK manifaktirè espesyal) — e li mache sou NENPÒT aparèy
 * Android (telefòn òdinè kou POS klon ki pa gen SDK entegre pa yo).
 *
 * Itilizatè a dwe "PÈ" (pair) enprimant lan nan Paramèt Bluetooth telefòn
 * nan yon fwa anvan, apre sa app la ka konekte avè l otomatikman.
 */
class BluetoothPrinterHelper {

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // Dènye mesaj erè a — pèmèt UI a montre l dirèkteman san bezwen Logcat/USB.
    var lastError: String? = null
        private set

    val isConnected: Boolean
        get() = socket?.isConnected == true

    /**
     * Lis tout aparèy Bluetooth ki DEJA pè ak telefòn/POS la.
     * Filtre pa non si w vle (egzanp: enprimant yo souvan gen "Printer",
     * "POS", "BT" nan non yo).
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        disconnect()
        lastError = null

        // Yon rechèch Bluetooth an kou ka anpeche/ralanti anpil koneksyon RFCOMM.
        val adapter = BluetoothAdapter.getDefaultAdapter()
        try {
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
        } catch (_: Exception) {
        }

        // 1. Eseye metòd "ofisyèl" SEGURIZE a (via rechèch SDP sou UUID SPP la).
        try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            this.socket = socket
            this.outputStream = socket.outputStream
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Metòd segurize echwe, m ap eseye ensekirize...", e)
            lastError = "Segurize: ${e.javaClass.simpleName} — ${e.message}"
        }

        // 2. Eseye vèsyon ENSEKIRIZE UUID SPP la — sa rezoud pifò erè
        //    "read failed, socket might closed or timeout" ak enprimant
        //    bon mache ki pa konplete koupláj (bonding) Android la kòrèkteman.
        try {
            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecureSocket.connect()
            this.socket = insecureSocket
            this.outputStream = insecureSocket.outputStream
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Metòd ensekirize echwe tou, m ap eseye fallback (kanal 1)...", e)
            lastError = (lastError ?: "") + " | Ensekirize: ${e.javaClass.simpleName} — ${e.message}"
        }

        // 3. Fallback final: koneksyon dirèk sou kanal RFCOMM 1 via refleksyon.
        return try {
            val fallbackSocket = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallbackSocket.connect()
            this.socket = fallbackSocket
            this.outputStream = fallbackSocket.outputStream
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback (kanal 1) echwe tou — enprimant la pa reponn.", e)
            lastError = (lastError ?: "") + " | Kanal1: ${e.javaClass.simpleName} — ${e.message}"
            disconnect()
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectByAddress(macAddress: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val device = adapter.getRemoteDevice(macAddress) ?: return false
        return connect(device)
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        socket = null
    }

    private fun write(bytes: ByteArray) {
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Echèk voye done bay enprimant la", e)
        }
    }

    fun printTestReceipt() {
        write(ESC_INIT)
        write(alignCenter())
        write(boldOn())
        write(textLine("PLUS GROUP"))
        write(boldOff())
        write(textLine("Tès enprimant Bluetooth — tout bon!"))
        write(feed(3))
        write(cutPaper())
    }

    fun printTicketReceipt(
        companyName: String,
        drawName: String,
        ticketNumber: String,
        numbers: String,
        betAmount: String,
        footerMessage: String,
    ) {
        write(ESC_INIT)
        write(alignCenter())
        write(boldOn())
        write(textLine(companyName))
        write(boldOff())
        write(textLine("BONNE CHANCE!"))
        write(feed(1))

        write(alignLeft())
        write(textLine("Tiraj: $drawName"))
        write(textLine("Tikè No: $ticketNumber"))
        write(textLine("Nimewo: $numbers"))
        write(textLine("Montan: $betAmount HTG"))
        write(feed(1))

        write(alignCenter())
        write(textLine(footerMessage))
        write(feed(4))
        write(cutPaper())
    }

    /**
     * Enprime yon Fich konplè, nan menm fòma resi bolèt tradisyonèl la:
     * tèt konpayi, telefòn, vandè/sikisal, dat/lè, nimewo Fich, tiraj +
     * total pa tiraj, liy yo (kòd 2-lèt + nimewo + pri, aliyen an kolòn),
     * total final, mesaj pye paj, ak yon kòd QR pou verifye Fich la.
     */
    fun printFicheReceipt(
        companyName: String,
        promoLine: String,
        phone: String,
        vendeur: String,
        dateTimeText: String,
        ficheNumber: String,
        drawName: String,
        drawTotal: String,
        lines: List<Triple<String, String, String>>, // (kòd, nimewo, pri fòmate)
        grandTotal: String,
        footerMessage: String,
        qrData: String?,
    ) {
        write(ESC_INIT)
        write(alignCenter())
        write(boldOn())
        write(textLine(companyName))
        write(boldOff())
        if (promoLine.isNotBlank()) write(textLine(promoLine))
        write(textLine("Tel: $phone"))
        write(textLine("Vendeur: $vendeur"))
        write(textLine("Fecha: $dateTimeText"))
        write(feed(1))
        write(alignLeft())
        write(textLine("Fiche: $ficheNumber"))
        write(textLine(DASHES))

        write(textLine("$drawName: $drawTotal"))

        for ((code, numero, prix) in lines) {
            write(textLine(twoColumnLine("$code   $numero", prix)))
        }

        write(textLine(DASHES))
        write(textLine(twoColumnLine("Total:  ${String.format("%03d", lines.size)}", grandTotal)))
        write(feed(1))

        write(alignCenter())
        if (footerMessage.isNotBlank()) {
            write(textLine(footerMessage))
        }
        write(feed(1))

        if (!qrData.isNullOrBlank()) {
            write(qrCode(qrData))
            write(feed(1))
        }

        write(feed(3))
        write(cutPaper())
    }

    /**
     * Enprime yon RAPÒ jenerik — menm fòma ak
     * `SunmiPrinterHelper.printReportReceipt()`.
     */
    fun printReportReceipt(
        companyName: String,
        branchCode: String,
        phone: String,
        reportTitle: String,
        headerLines: List<Pair<String, String>>,
        bodyFields: List<Pair<String, String>>,
        footerFields: List<Pair<String, String>> = emptyList(),
    ) {
        write(ESC_INIT)
        write(alignCenter())
        write(boldOn())
        write(textLine(companyName))
        write(boldOff())
        if (branchCode.isNotBlank()) write(textLine(branchCode))
        if (phone.isNotBlank()) write(textLine("Tel: $phone"))
        write(textLine(reportTitle))
        write(feed(1))

        write(alignLeft())
        for ((label, value) in headerLines) {
            write(textLine("$label: $value"))
        }
        write(textLine(DASHES))
        for ((label, value) in bodyFields) {
            write(textLine(twoColumnLine("$label:", value)))
        }
        if (footerFields.isNotEmpty()) {
            write(textLine(DASHES))
            for ((label, value) in footerFields) {
                write(textLine(twoColumnLine("$label:", value)))
            }
        }

        write(feed(3))
        write(cutPaper())
    }

    // Aliyen tèks agoch ak yon valè adwat sou menm liy, ak espas ki
    // ranpli ant yo (sipoze lajè papye ~32 karaktè, 58mm estanda).
    private fun twoColumnLine(left: String, right: String, width: Int = 32): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right else "$left $right"
    }

    // Kòmand ESC/POS estanda pou enprime yon kòd QR (fonksyon 165/"GS ( k").
    // Sipòte pa prèske tout enprimant tèmik jenerik ki egziste.
    private fun qrCode(data: String, moduleSize: Int = 5): ByteArray {
        val bytes = data.toByteArray(Charsets.UTF_8)
        val storeLen = bytes.size + 3
        val pL = (storeLen and 0xFF).toByte()
        val pH = ((storeLen shr 8) and 0xFF).toByte()

        val out = java.io.ByteArrayOutputStream()
        // Chwazi modèl QR (modèl 2)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // Gwosè module a
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleSize.toByte()))
        // Nivo koreksyon erè (48 = L)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
        // Estoke done yo
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(bytes)
        // Enprime kòd la
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        return out.toByteArray()
    }

    // --- Kòmand ESC/POS debaz ---

    private fun textLine(text: String): ByteArray =
        (text + "\n").toByteArray(Charsets.UTF_8)

    private fun alignLeft(): ByteArray = byteArrayOf(0x1B, 0x61, 0x00)
    private fun alignCenter(): ByteArray = byteArrayOf(0x1B, 0x61, 0x01)
    private fun boldOn(): ByteArray = byteArrayOf(0x1B, 0x45, 0x01)
    private fun boldOff(): ByteArray = byteArrayOf(0x1B, 0x45, 0x00)
    private fun feed(lines: Int): ByteArray = ByteArray(lines) { 0x0A }
    private fun cutPaper(): ByteArray = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    companion object {
        private const val TAG = "BluetoothPrinterHelper"
        private const val DASHES = "--------------------------------"
        private const val ESC_INIT_BYTE: Byte = 0x1B
        private val ESC_INIT = byteArrayOf(ESC_INIT_BYTE, 0x40) // ESC @ = reinisyalize enprimant

        // UUID estanda Serial Port Profile (SPP) — itilize pa prèske tout
        // enprimant tèmik Bluetooth ki egziste.
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}