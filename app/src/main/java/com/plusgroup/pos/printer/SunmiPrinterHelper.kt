package com.plusgroup.pos.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import woyou.aidlservice.jiuiv5.IWoyouService

/**
 * Kouch entegrasyon ak sèvis enprimant SUNMI a.
 *
 * IMPÒTAN: Sa a mache SÈLMAN sou aparèy SUNMI (li itilize sèvis sistèm
 * "woyou.aidlservice.jiuiv5" ki deja enstale nan ROM SUNMI a). Li p ap
 * mache sou lòt telefòn/tablèt Android òdinè.
 *
 * Ou dwe telechaje vrè fichye `IWoyouService.aidl` la nan pòtal devlopè
 * SUNMI a epi mete l nan:
 *   app/src/main/aidl/woyou/aidlservice/jiuiv5/IWoyouService.aidl
 * anvan pwojè a ka konpile.
 *
 * NÒT TEKNIK enpòtan (aprè anpil tès sou yon aparèy SUNMI V2 reyèl,
 * API 25 / Android 7.1.1):
 * 1. `printText()` senp SANS font eksplisit sanble itilize yon codaj CJK
 *    (Chinwa) pa default sou ansyen sèvis sa a — li fè tèks Latin/Kreyòl
 *    vin defòme an senbòl Chinwa (mojibake) san jete okenn erè. SÈL
 *    `printTextWithFont()` jere codaj Latin kòrèkteman, kidonk nou sèvi
 *    ak li pou TOUT liy, pa jis kèk grenn.
 * 2. `printerInit()` bezwen yon délè pi long (~200ms) anvan premye
 *    kòmand tèks la, paske sèvis la sanble bezwen "chaje" font/codaj la;
 *    san sa, premye karaktè(s) yo ka rete defòme menm ak printTextWithFont.
 * 3. Chak kòmand AIDL swiv pa yon ti délè (~50ms) — san sa, "race
 *    condition" fè premye karaktè yon liy vin defòme lè l swiv yon
 *    lineWrap() twò vit.
 * 4. Tout sekans enprime a egzekite sou yon THREAD SEPARE (pa sou
 *    main/UI thread), paske Thread.sleep() itilize pou jere pwoblèm anwo
 *    yo ta jele ekran an si l te egzekite sou main thread.
 * 5. `lineWrap()` final la dwe ase gwo (8, pa 3-5) pou fè tout rès fich
 *    la fizikman soti anvan koupe a, sinon dènye pati a rete "kwense"
 *    anndan mekanis enprimant lan.
 */
class SunmiPrinterHelper(private val context: Context) {

    private var woyouService: IWoyouService? = null
    var isConnected: Boolean = false
        private set

    private var onReadyCallback: (() -> Unit)? = null

    private val connService = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            woyouService = null
            isConnected = false
            Log.w(TAG, "Sèvis enprimant SUNMI a dekonekte")
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            woyouService = IWoyouService.Stub.asInterface(service)
            isConnected = true
            Log.i(TAG, "Sèvis enprimant SUNMI a konekte")
            onReadyCallback?.invoke()
        }
    }

    /**
     * Konekte ak sèvis enprimant SUNMI a. Rele sa nan onCreate() ekran an,
     * epi tann `onReady` anvan w eseye enprime.
     */
    fun connect(onReady: (() -> Unit)? = null) {
        onReadyCallback = onReady
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            context.bindService(intent, connService, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Echèk konekte ak sèvis enprimant SUNMI a", e)
        }
    }

    fun disconnect() {
        if (isConnected) {
            try {
                context.unbindService(connService)
            } catch (e: Exception) {
                Log.e(TAG, "Echèk dekonekte", e)
            }
            isConnected = false
        }
    }

    /** Ti délè ant chak kòmand AIDL — evite "race condition" mojibake. */
    private fun pause(ms: Long = 15) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            // Pa gwo zafè si sa entèwonp — kontinye kanmenm.
        }
    }

    /**
     * Enprime yon tès senp — itilize sa pou verifye entegrasyon an mache
     * anvan w bati resi konplè tikè yo. Egzekite sou yon thread separe.
     */
    /**
     * Fòse kodpaj karaktè enprimant lan sou PC437 (Standard Europe/USA) via
     * yon kòmand ESC/POS brit: ESC t 0 (0x1B 0x74 0x00). Sa rezoud pwoblèm
     * "mojibake" (tèks Latin ki tradui an senbòl Chinwa) ki rive lè
     * enprimant lan rete sou yon kodpaj GBK/CJK pa default.
     */
    private fun forceLatinCodepage(svc: IWoyouService) {
        try {
            svc.sendRAWData(byteArrayOf(0x1B, 0x74, 0x00), null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk fòse kodpaj Latin", e)
        }
    }

    fun printTestReceipt() {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        Thread {
            Log.e(TAG, "=== MAKI_VESYON_9 EGZEKITE (printTestReceipt) ===")
            try {
                svc.printerInit(null)
                pause(120) // délè apre init pou font/codaj la chaje
                forceLatinCodepage(svc)
                pause(60)

                svc.setAlignment(1, null)
                pause()

                svc.printTextWithFont("PLUS GROUP\n", null, 28f, null)
                pause()

                svc.printTextWithFont("Test enprimant - tout bon\n", null, 24f, null)
                pause()

                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime tès la", e)
            }
        }.start()
    }

    /**
     * Enprime yon resi tikè bolèt. Egzekite sou yon thread separe.
     */
    fun printTicketReceipt(
        companyName: String,
        drawName: String,
        ticketNumber: String,
        numbers: String,
        betAmount: String,
        footerMessage: String,
    ) {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        Thread {
            try {
                svc.printerInit(null)
                pause(120)
                forceLatinCodepage(svc)
                pause(60)

                svc.setAlignment(1, null)
                pause()
                svc.printTextWithFont("$companyName\n", null, 28f, null)
                pause()
                svc.printTextWithFont("BONNE CHANCE!\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(0, null)
                pause()
                svc.printTextWithFont("Tiraj: $drawName\n", null, 24f, null)
                pause()
                svc.printTextWithFont("Tikè No: $ticketNumber\n", null, 24f, null)
                pause()
                svc.printTextWithFont("Nimewo: $numbers\n", null, 24f, null)
                pause()
                svc.printTextWithFont("Montan: $betAmount HTG\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(1, null)
                pause()
                svc.printQRCode(ticketNumber, 8, 0, null)
                pause()
                svc.lineWrap(1, null)
                pause()
                svc.printTextWithFont("$footerMessage\n", null, 24f, null)
                pause()

                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime resi tikè a", e)
            }
        }.start()
    }

    /**
     * Enprime yon Fich konplè, nan menm fòma resi bolèt tradisyonèl la
     * (menm fòma ak `BluetoothPrinterHelper.printFicheReceipt`).
     * Egzekite sou yon thread separe pou l pa jele ekran an.
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
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        Thread {
            Log.e(TAG, "=== MAKI_VESYON_9 EGZEKITE (printFicheReceipt) ===")
            try {
                svc.printerInit(null)
                pause(120) // délè apre init pou font/codaj la chaje
                forceLatinCodepage(svc)
                pause(60)

                svc.setAlignment(1, null)
                pause()
                svc.printTextWithFont("$companyName\n", null, 28f, null)
                pause()
                if (promoLine.isNotBlank()) {
                    svc.printTextWithFont("$promoLine\n", null, 24f, null)
                    pause()
                }

                svc.setAlignment(0, null)
                pause()
                if (phone.isNotBlank()) {
                    svc.printTextWithFont("Tel: $phone\n", null, 24f, null)
                    pause()
                }
                svc.printTextWithFont("Vendeur: $vendeur\n", null, 24f, null)
                pause()
                svc.printTextWithFont("Fecha: $dateTimeText\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.printTextWithFont("Fiche: $ficheNumber\n", null, 24f, null)
                pause()
                svc.printTextWithFont("$DASHES\n", null, 24f, null)
                pause()
                svc.printTextWithFont("$drawName: $drawTotal\n", null, 24f, null)
                pause()

                for ((code, numero, prix) in lines) {
                    svc.printTextWithFont(twoColumnLine("$code   $numero", prix) + "\n", null, 24f, null)
                    pause()
                }

                svc.printTextWithFont("$DASHES\n", null, 24f, null)
                pause()
                svc.printTextWithFont(twoColumnLine("Total:  ${String.format("%03d", lines.size)}", grandTotal) + "\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(1, null)
                pause()
                if (footerMessage.isNotBlank()) {
                    svc.printTextWithFont("$footerMessage\n", null, 24f, null)
                    pause()
                    svc.lineWrap(1, null)
                    pause()
                }

                if (!qrData.isNullOrBlank()) {
                    svc.printQRCode(qrData, 8, 0, null)
                    pause()
                    svc.lineWrap(3, null) // ekstra espas apre QR pou l pa koupe twò raz
                    pause()
                }

                // Délè final pi gwo — asire tout papye a fizikman soti
                // anvan koupe a, sinon rès la rete kwense anndan.
                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime Fich la", e)
            }
        }.start()
    }

    /**
     * Enprime yon RAPÒ jenerik (Rapport partiel, Fin tirage, Fiche
     * gagnant, Transactions, Fich elimine) nan menm fòma ak resi DVN
     * Lotto a: antèt konpayi santre, kèk liy antèt agoch (Date/Vendeur/
     * Pos...), yon liy `----`, epi chan "Etikèt: Valè" aliyen an 2 kolòn.
     * `footerFields` opsyonèl ajoute yon dezyèm seksyon apre yon dezyèm
     * liy `----` (itil pou Depot/Retrait/Balance nan F.TIRAGE).
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
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        Thread {
            try {
                svc.printerInit(null)
                pause(120)
                forceLatinCodepage(svc)
                pause(60)

                svc.setAlignment(1, null)
                pause()
                svc.printTextWithFont("$companyName\n", null, 28f, null)
                pause()
                if (branchCode.isNotBlank()) {
                    svc.printTextWithFont("$branchCode\n", null, 24f, null)
                    pause()
                }
                if (phone.isNotBlank()) {
                    svc.printTextWithFont("Tel: $phone\n", null, 24f, null)
                    pause()
                }
                svc.printTextWithFont("$reportTitle\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(0, null)
                pause()
                for ((label, value) in headerLines) {
                    svc.printTextWithFont("$label: $value\n", null, 24f, null)
                    pause()
                }

                svc.printTextWithFont("$DASHES\n", null, 24f, null)
                pause()
                for ((label, value) in bodyFields) {
                    svc.printTextWithFont(twoColumnLine("$label:", value) + "\n", null, 24f, null)
                    pause()
                }

                if (footerFields.isNotEmpty()) {
                    svc.printTextWithFont("$DASHES\n", null, 24f, null)
                    pause()
                    for ((label, value) in footerFields) {
                        svc.printTextWithFont(twoColumnLine("$label:", value) + "\n", null, 24f, null)
                        pause()
                    }
                }

                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime rapò a", e)
            }
        }.start()
    }

    // Aliyen tèks agoch ak yon valè adwat sou menm liy (menm lojik ak
    // BluetoothPrinterHelper, sipoze lajè ~32 karaktè).
    private fun twoColumnLine(left: String, right: String, width: Int = 32): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right else "$left $right"
    }

    companion object {
        private const val TAG = "SunmiPrinterHelper"
        private const val DASHES = "--------------------------------"
    }
}