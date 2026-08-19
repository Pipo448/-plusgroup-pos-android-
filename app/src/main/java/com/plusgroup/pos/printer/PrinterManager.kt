package com.plusgroup.pos.printer

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * Kouch inifye ki kache diferans ant enprimant SUNMI (entegre) ak
 * enprimant Bluetooth eksitèn. Rès app la rele SÈLMAN PrinterManager,
 * li pa bezwen konnen ki kalite enprimant ki reyèlman itilize.
 *
 * Deteksyon:
 *   - Si aparèy la se yon SUNMI (Build.MANUFACTURER/BRAND genyen "sunmi"),
 *     itilize sèvis entegre SUNMI a.
 *   - Sinon, itilize enprimant Bluetooth eksitèn (itilizatè a chwazi l
 *     yon sèl fwa nan Paramèt app la).
 */
class PrinterManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("plus_pos_printer", Context.MODE_PRIVATE)

    private val isSunmiDevice: Boolean = detectSunmi()

    private var sunmiHelper: SunmiPrinterHelper? = null
    private val bluetoothHelper = BluetoothPrinterHelper()

    val printerType: PrinterType
        get() = if (isSunmiDevice) PrinterType.SUNMI_INTEGRATED else PrinterType.BLUETOOTH_EXTERNAL

    // Dènye mesaj erè Bluetooth la — pèmèt MainActivity montre l dirèkteman
    // sou ekran an (Toast), san bezwen Logcat/kab USB.
    val lastPrinterError: String?
        get() = bluetoothHelper.lastError
            ?: if (getSavedPrinterAddress() == null) {
                "Pa gen okenn enprimant chwazi anndan app la. Klike 'Chwazi enprimant Bluetooth' pou chwazi l, menm si li deja pè nan Paramèt Android."
            } else null

    fun connect(onReady: (() -> Unit)? = null) {
        if (isSunmiDevice) {
            sunmiHelper = SunmiPrinterHelper(context).also {
                it.connect(onReady)
            }
        } else {
            // Pou Bluetooth, konekte ak dènye enprimant itilizatè a te chwazi a
            // (si genyen). Si pa gen okenn anrejistre, `isReady()` ap fo.
            val savedAddress = getSavedPrinterAddress()
            if (savedAddress != null) {
                Thread {
                    bluetoothHelper.connectByAddress(savedAddress)
                    onReady?.invoke()
                }.start()
            } else {
                // ENPÒTAN: si pa gen okenn enprimant sove, rele callback la
                // TOUJOU — sinon ekran an rete "ap konekte..." pou tout tan
                // san rezon, paske pèsonn pa t janm chwazi yon enprimant
                // anndan app la (menm si l pè nan Paramèt Android).
                onReady?.invoke()
            }
        }
    }

    fun disconnect() {
        sunmiHelper?.disconnect()
        bluetoothHelper.disconnect()
    }

    fun isReady(): Boolean {
        return if (isSunmiDevice) sunmiHelper?.isConnected == true
        else bluetoothHelper.isConnected
    }

    fun printTestReceipt() {
        if (isSunmiDevice) sunmiHelper?.printTestReceipt()
        else bluetoothHelper.printTestReceipt()
    }

    fun printTicketReceipt(
        companyName: String,
        drawName: String,
        ticketNumber: String,
        numbers: String,
        betAmount: String,
        footerMessage: String,
    ) {
        if (isSunmiDevice) {
            sunmiHelper?.printTicketReceipt(companyName, drawName, ticketNumber, numbers, betAmount, footerMessage)
        } else {
            bluetoothHelper.printTicketReceipt(companyName, drawName, ticketNumber, numbers, betAmount, footerMessage)
        }
    }

    /**
     * Enprime Fich la nan fòma resi bolèt konplè (ak QR).
     * Sipòte tou de kalite enprimant: SUNMI entegre ak Bluetooth eksitèn.
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
        lines: List<Triple<String, String, String>>,
        grandTotal: String,
        footerMessage: String,
        qrData: String?,
    ) {
        if (isSunmiDevice) {
            sunmiHelper?.printFicheReceipt(
                companyName, promoLine, phone, vendeur, dateTimeText, ficheNumber,
                drawName, drawTotal, lines, grandTotal, footerMessage, qrData,
            )
        } else {
            bluetoothHelper.printFicheReceipt(
                companyName, promoLine, phone, vendeur, dateTimeText, ficheNumber,
                drawName, drawTotal, lines, grandTotal, footerMessage, qrData,
            )
        }
    }

    /**
     * Enprime yon RAPÒ jenerik (Rapport partiel, Fin tirage, Fiche
     * gagnant, Transactions, Fich elimine) — antèt konpayi, kèk liy
     * kontèks (Date/Vendeur/Pos...), yon seksyon chan "Etikèt: Valè",
     * ak yon dezyèm seksyon opsyonèl (`footerFields`, egzanp
     * Depot/Retrait/Balance).
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
        if (isSunmiDevice) {
            sunmiHelper?.printReportReceipt(
                companyName, branchCode, phone, reportTitle, headerLines, bodyFields, footerFields,
            )
        } else {
            bluetoothHelper.printReportReceipt(
                companyName, branchCode, phone, reportTitle, headerLines, bodyFields, footerFields,
            )
        }
    }

    // --- Jesyon chwa enprimant Bluetooth (sèlman itilize si pa SUNMI) ---

    fun getPairedBluetoothPrinters(): List<BluetoothDevice> = bluetoothHelper.getPairedDevices()

    fun selectBluetoothPrinter(device: BluetoothDevice): Boolean {
        val macAddress = device.address
        prefs.edit().putString(KEY_PRINTER_MAC, macAddress).apply()
        return bluetoothHelper.connect(device)
    }

    fun getSavedPrinterAddress(): String? = prefs.getString(KEY_PRINTER_MAC, null)

    fun hasConfiguredPrinter(): Boolean = isSunmiDevice || getSavedPrinterAddress() != null

    private fun detectSunmi(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        return manufacturer.contains("sunmi") || brand.contains("sunmi")
    }

    enum class PrinterType {
        SUNMI_INTEGRATED,
        BLUETOOTH_EXTERNAL,
    }

    companion object {
        private const val KEY_PRINTER_MAC = "printer_mac"
    }
}