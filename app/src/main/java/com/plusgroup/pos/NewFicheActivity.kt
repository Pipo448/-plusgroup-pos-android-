package com.plusgroup.pos

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plusgroup.pos.databinding.ActivityNewFicheBinding
import com.plusgroup.pos.databinding.ItemFicheLineBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.LotteryGame
import com.plusgroup.pos.network.models.SellTicketRequest
import com.plusgroup.pos.printer.PrinterManager
import com.plusgroup.pos.util.DeviceIdHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Ekran "Nouvèl Fich" — vann tikè.
 *
 * Chak "liy" se yon (tiraj + nimewo + pri). Bouton rapid yo (BPaire, Grap,
 * P0-P9) jenere otomatikman 10 liy ak MENM pri a chak fwa, olye ajan an
 * antre chak nimewo yon pa yon.
 *
 * Chak liy ka EFASE (kòbèy) oswa MODIFYE pri li (kreyon) apa, san l pa
 * afekte lòt liy yo.
 *
 * Yon SÈL bouton (ikòn enprime anlè a) fè tout bagay: soumèt chak liy bay
 * backend la, epi si tout liy yo pase, enprime resi a otomatikman.
 *
 * FICHE_NUMBER: tout liy yon menm fich jenere e voye AK MENM `ficheNumber`
 * la, pou backend lan ka gwoupe yo ansanm nan "Mes fiches" (olye chak liy
 * parèt kòm yon fich apa).
 *
 * PREFILL (REJWE): si ekran sa a louvri ak "PREFILL_LINES_JSON" nan
 * Intent la (soti nan MyFichesActivity, bouton "Rejwe"), nou chaje boul
 * yo tou prepli, editab, epi ajan an ka ajoute/retire/chanje tiraj anvan
 * l soumèt kòm yon NOUVO vant (nouvo fiche_number, pa yon modifikasyon
 * ansyen fich la).
 */
class NewFicheActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewFicheBinding

    private var openDraws: List<Draw> = emptyList()
    private var selectedDraw: Draw? = null

    private var activeGames: List<LotteryGame> = emptyList()
    private var selectedGame: LotteryGame? = null

    private data class FicheLine(
        val drawName: String,
        val numero: String,
        var price: Double,
        val category: String,
        val optionLabel: String? = null,
    )

    private data class PrefillLine(val numero: String, val price: Double)

    private val lines = mutableListOf<FicheLine>()

    private val moneyFormat = DecimalFormat("#,##0.00")
    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }

    private object CompanyInfoCache {
        var companyName: String? = null
        var vendeur: String? = null
        var footerMessage: String? = null
        var loaded: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewFicheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearAll.setOnClickListener {
            lines.clear()
            refreshLinesList()
            refreshTotal()
        }
        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "Pataje — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnPrint.setOnClickListener { submitFiche() }
        binding.btnEditGameLabel.setOnClickListener { showGamePicker() }

        binding.tvChwaziTiraj.setOnClickListener { showDrawPicker() }

        binding.btnBPaire.setOnClickListener { addQuickCategory(QuickCategory.B_PAIRE) }
        binding.btnGrap.setOnClickListener { addQuickCategory(QuickCategory.GRAP) }
        binding.btnP0.setOnClickListener { addQuickCategory(QuickCategory.pick(0)) }
        binding.btnP1.setOnClickListener { addQuickCategory(QuickCategory.pick(1)) }
        binding.btnP2.setOnClickListener { addQuickCategory(QuickCategory.pick(2)) }
        binding.btnP3.setOnClickListener { addQuickCategory(QuickCategory.pick(3)) }
        binding.btnP4.setOnClickListener { addQuickCategory(QuickCategory.pick(4)) }
        binding.btnP5.setOnClickListener { addQuickCategory(QuickCategory.pick(5)) }
        binding.btnP6.setOnClickListener { addQuickCategory(QuickCategory.pick(6)) }
        binding.btnP7.setOnClickListener { addQuickCategory(QuickCategory.pick(7)) }
        binding.btnP8.setOnClickListener { addQuickCategory(QuickCategory.pick(8)) }
        binding.btnP9.setOnClickListener { addQuickCategory(QuickCategory.pick(9)) }
        binding.btnMariage.setOnClickListener { addMariageLine() }
        binding.btnLoto4.setOnClickListener { showLoto4OptionsDialog() }
        binding.btnLoto5.setOnClickListener { showLoto5OptionsDialog() }

        binding.btnChooseMariage.setOnClickListener { addMariageLine() }
        binding.btnChooseLoto4.setOnClickListener { showLoto4OptionsDialog() }

        binding.etBoulLa.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val len = s?.toString()?.trim()?.length ?: 0
                binding.llMariageLoto4Chooser.visibility =
                    if (len == 4) android.view.View.VISIBLE else android.view.View.GONE
            }
        })

        binding.btnAntre.setOnClickListener { addManualLine() }

        loadDrawsAndGames()
        refreshTotal()
    }

    // ==================== CHAJE TIRAJ AK JWÈT ====================

    private fun loadDrawsAndGames() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)

                val drawsRes = api.getDraws("open")
                openDraws = drawsRes.body()?.data ?: emptyList()

                val gamesRes = api.getGames()
                activeGames = gamesRes.body()?.data ?: emptyList()
                selectedGame = activeGames.firstOrNull()
                binding.tvFicheLabel.text = (selectedGame?.name ?: "BORLETTE").uppercase()

                applyPrefillIfAny()
            } catch (e: Exception) {
                Toast.makeText(
                    this@NewFicheActivity,
                    "Pa t ka chaje tiraj/jwèt yo: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // ==================== PREFILL (soti nan "Rejwe" nan Mes fiches) ====================

    private fun applyPrefillIfAny() {
        val prefillDrawName = intent.getStringExtra("PREFILL_DRAW_NAME")
        val prefillLinesJson = intent.getStringExtra("PREFILL_LINES_JSON")

        if (prefillDrawName != null) {
            val match = openDraws.firstOrNull { it.name == prefillDrawName }
            if (match != null) {
                selectedDraw = match
                binding.tvChwaziTiraj.text = match.name ?: "CHWAZI TIRAJ"
            } else {
                Toast.makeText(
                    this,
                    "Tiraj '$prefillDrawName' pa ouvè kounye a — chwazi yon lòt tiraj",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        if (!prefillLinesJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawLines: List<Map<String, Any>> = Gson().fromJson(prefillLinesJson, type)
                val drawName = selectedDraw?.name ?: prefillDrawName ?: "—"

                rawLines.forEach { raw ->
                    val numero = (raw["numero"] as? String)?.trim() ?: return@forEach
                    val price = (raw["price"] as? Double) ?: 0.0
                    if (numero.isEmpty() || price <= 0) return@forEach
                    lines.add(FicheLine(drawName, numero, price, categoryForNumero(numero)))
                }
                refreshLinesList()
                refreshTotal()

                if (rawLines.isNotEmpty()) {
                    Toast.makeText(this, "Fich chaje — ajoute/retire liy si w vle, epi enprime", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erè chaje ansyen fich la: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun categoryForNumero(numero: String): String = when {
        numero.contains("*") -> "MARIAGE"
        numero.length == 2 -> "BORLETTE"
        numero.length == 3 -> "LOTO3"
        numero.length == 4 -> "LOTO4"
        numero.length == 5 -> "LOTO5"
        else -> "BORLETTE"
    }

    private fun showDrawPicker() {
        if (openDraws.isEmpty()) {
            Toast.makeText(this, "Pa gen tiraj ouvè kounye a", Toast.LENGTH_SHORT).show()
            return
        }
        val names = openDraws.map { it.name ?: it.id }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("CHWAZI TIRAJ")
            .setSingleChoiceItems(names, -1) { dialog, which ->
                selectedDraw = openDraws[which]
                binding.tvChwaziTiraj.text = selectedDraw?.name ?: "CHWAZI TIRAJ"
                dialog.dismiss()
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    private fun showGamePicker() {
        if (activeGames.isEmpty()) {
            Toast.makeText(this, "Pa gen kategori jwèt disponib", Toast.LENGTH_SHORT).show()
            return
        }
        val names = activeGames.map { it.name ?: it.id }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("CHWAZI KATEGORI")
            .setSingleChoiceItems(names, -1) { dialog, which ->
                selectedGame = activeGames[which]
                binding.tvFicheLabel.text = (selectedGame?.name ?: "BORLETTE").uppercase()
                dialog.dismiss()
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    // ==================== KATEGORI RAPID (BPaire / Grap / Pick N) ====================

    private sealed class QuickCategory {
        object BPaire : QuickCategory()
        object Grap : QuickCategory()
        data class Pick(val digit: Int) : QuickCategory()

        companion object {
            val B_PAIRE = BPaire
            val GRAP = Grap
            fun pick(digit: Int) = Pick(digit)
        }
    }

    private fun addQuickCategory(category: QuickCategory) {
        if (!requireDrawSelected()) return

        val numbers: List<String> = when (category) {
            is QuickCategory.BPaire -> (0..9).map { "$it$it" }
            is QuickCategory.Grap -> (0..9).map { "$it$it$it" }
            is QuickCategory.Pick -> (0..9).map { d -> "$d${category.digit}" }
        }
        promptForPrice { price ->
            val drawName = selectedDraw?.name ?: "—"
            numbers.forEach { num -> lines.add(FicheLine(drawName, num, price, "BORLETTE")) }
            refreshLinesList()
            refreshTotal()
        }
    }

    private fun promptForPrice(onPrice: (Double) -> Unit) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        AlertDialog.Builder(this)
            .setTitle("Pri pou chak nimewo")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val price = input.text.toString().toDoubleOrNull()
                if (price == null || price <= 0) {
                    Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
                } else {
                    onPrice(price)
                }
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    // ==================== ANTRE MANYÈL (oto-deteksyon selon konbyen chif) ====================

    private fun addManualLine() {
        if (!requireDrawSelected()) return

        val numero = binding.etBoulLa.text.toString().trim()
        val montantText = binding.etMontant.text.toString().trim()

        if (numero.isEmpty()) {
            Toast.makeText(this, "Antre yon nimewo", Toast.LENGTH_SHORT).show()
            return
        }
        val montant = montantText.toDoubleOrNull()
        if (montant == null || montant <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val category = when (numero.length) {
            2 -> "BORLETTE"
            3 -> "LOTO3"
            4 -> {
                Toast.makeText(this, "4 chif: chwazi \"Mariage\" oswa \"Loto4\" anba a", Toast.LENGTH_LONG).show()
                return
            }
            5 -> "LOTO5"
            else -> {
                Toast.makeText(this, "Nimewo dwe 2, 3, 4, oswa 5 chif", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val drawName = selectedDraw?.name ?: "—"
        lines.add(FicheLine(drawName, numero, montant, category))
        binding.etBoulLa.text?.clear()
        refreshLinesList()
        refreshTotal()
    }

    // ==================== MARIAGE ====================
    private fun addMariageLine() {
        if (!requireDrawSelected()) return

        val rawInput = binding.etBoulLa.text.toString().trim()
        val montant = binding.etMontant.text.toString().trim().toDoubleOrNull()

        val numero = when {
            rawInput.matches(Regex("^\\d{4}$")) -> rawInput.substring(0, 2) + "*" + rawInput.substring(2, 4)
            rawInput.matches(Regex("^\\d{2}\\*\\d{2}$")) -> rawInput
            else -> {
                Toast.makeText(this, "Antre 4 chif (egzanp 2532) pou Mariage", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (montant == null || montant <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val drawName = selectedDraw?.name ?: "—"
        lines.add(FicheLine(drawName, numero, montant, "MARIAGE"))
        binding.etBoulLa.text?.clear()
        refreshLinesList()
        refreshTotal()
    }

    // ==================== LOTO4 ====================
    private fun showLoto4OptionsDialog() {
        if (!requireDrawSelected()) return

        val numero = binding.etBoulLa.text.toString().trim()
        val price = binding.etMontant.text.toString().trim().toDoubleOrNull()

        if (!numero.matches(Regex("^\\d{4}$"))) {
            Toast.makeText(this, "Antre yon nimewo 4 chif pou Loto4", Toast.LENGTH_SHORT).show()
            return
        }
        if (price == null || price <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val vire = numero.substring(2, 4) + numero.substring(0, 2)

        val options = listOf(
            Triple("L4O1", numero, "01"),
            Triple("L4O1", vire, "01"),
            Triple("L4O2", numero, "02"),
            Triple("L4O2", vire, "02"),
            Triple("L4O3", numero, "03"),
            Triple("L4O3", vire, "03"),
        )

        showOptionsCheckboxDialog("OPTIONS LOTO 4", options, price, "LOTO4")
    }

    // ==================== LOTO5 ====================
    private fun showLoto5OptionsDialog() {
        if (!requireDrawSelected()) return

        val numero = binding.etBoulLa.text.toString().trim()
        val price = binding.etMontant.text.toString().trim().toDoubleOrNull()

        if (!numero.matches(Regex("^\\d{5}$"))) {
            Toast.makeText(this, "Antre yon nimewo 5 chif pou Loto5", Toast.LENGTH_SHORT).show()
            return
        }
        if (price == null || price <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val options = listOf(
            Triple("Option 1", numero, "01"),
            Triple("Option 2", numero, "02"),
            Triple("Option 3", numero, "03"),
        )

        showOptionsCheckboxDialog("OPTIONS LOTO 5", options, price, "LOTO5")
    }

    private fun showOptionsCheckboxDialog(
        title: String,
        options: List<Triple<String, String, String>>,
        price: Double,
        category: String,
    ) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val checkBoxes = mutableListOf<android.widget.CheckBox>()

        for ((label, numero, _) in options) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val checkBox = android.widget.CheckBox(this).apply {
                text = "$label -> $numero"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val priceLabel = TextView(this).apply {
                text = moneyFormat.format(price)
            }
            checkBoxes.add(checkBox)
            row.addView(checkBox)
            row.addView(priceLabel)
            container.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("VALIDER") { _, _ ->
                val drawName = selectedDraw?.name ?: "—"
                var addedCount = 0
                checkBoxes.forEachIndexed { i, cb ->
                    if (cb.isChecked) {
                        val (_, numero, optionLabel) = options[i]
                        lines.add(FicheLine(drawName, numero, price, category, optionLabel))
                        addedCount++
                    }
                }
                if (addedCount == 0) {
                    Toast.makeText(this, "Ou pa koche okenn opsyon", Toast.LENGTH_SHORT).show()
                } else {
                    binding.etBoulLa.text?.clear()
                    refreshLinesList()
                    refreshTotal()
                }
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    private fun requireDrawSelected(): Boolean {
        if (selectedDraw == null) {
            Toast.makeText(this, "Chwazi yon Tiraj anvan ou ka vann", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // ==================== JENERE NIMEWO FICH ====================
    // Yon SÈL nimewo pou TOUT liy nan menm fich la — jenere yon sèl fwa
    // pandan `submitFiche()`, epi voye ak CHAK rekèt sellTicket, epi
    // reyitilize pou enprime a (olye 2 nimewo diferan tankou anvan).
    private fun generateFicheNumber(): String {
        val raw = (System.currentTimeMillis() % 1_000_000_000L).toString().padStart(12, '0').takeLast(12)
        return "${raw.substring(0, 4)}-${raw.substring(4, 8)}-${raw.substring(8, 12)}"
    }

    // ==================== ENPRIME FICH LA ====================
    private fun printCurrentFiche(ficheNumber: String) {
        if (lines.isEmpty()) {
            Toast.makeText(this, "Pa gen liy pou enprime", Toast.LENGTH_SHORT).show()
            return
        }
        val drawName = selectedDraw?.name ?: "—"
        val total = lines.sumOf { it.price }

        val dateFormat = java.text.SimpleDateFormat("dd/MMM/yyyy hh:mm a", java.util.Locale.US)
        val dateTimeText = dateFormat.format(java.util.Date())

        val printLines: List<Triple<String, String, String>> = lines.map { line ->
            val code = when (line.category) {
                "BORLETTE" -> "BO"
                "LOTO3" -> "LT"
                "LOTO4" -> "L4"
                "LOTO5" -> "L5"
                "MARIAGE" -> "MA"
                else -> line.category.take(2).uppercase()
            }
            Triple(code, line.numero, moneyFormat.format(line.price))
        }

        lifecycleScope.launch {
            if (!CompanyInfoCache.loaded) {
                try {
                    val api = ApiClient.getService(applicationContext)
                    val profile = api.getProfile().body()?.data
                    CompanyInfoCache.companyName = profile?.tenantName?.uppercase()
                    CompanyInfoCache.vendeur = profile?.branchName ?: profile?.fullName

                    val settings = api.getCompanySettings().body()?.data ?: emptyList()
                    val msg = settings.firstOrNull { it.key == "message" }?.value
                    if (!msg.isNullOrBlank()) CompanyInfoCache.footerMessage = msg
                } catch (_: Exception) {
                } finally {
                    CompanyInfoCache.loaded = true
                }
            }

            val companyName = CompanyInfoCache.companyName ?: "PLUS GROUP"
            val vendeur = CompanyInfoCache.vendeur ?: "—"
            val footerMessage = CompanyInfoCache.footerMessage ?: "Mèsi pou konfyans ou!"

            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(
                            this@NewFicheActivity,
                            "Enprimant pa konekte. Ale nan Paramèt/MainActivity pou konekte l.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@runOnUiThread
                    }
                    printerManager.printFicheReceipt(
                        companyName = companyName,
                        promoLine = "",
                        phone = "",
                        vendeur = vendeur,
                        dateTimeText = dateTimeText,
                        ficheNumber = ficheNumber,
                        drawName = drawName,
                        drawTotal = moneyFormat.format(total),
                        lines = printLines,
                        grandTotal = moneyFormat.format(total),
                        footerMessage = footerMessage,
                        qrData = ficheNumber,
                    )
                    Toast.makeText(this@NewFicheActivity, "Fich enprime avèk siksè!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== LIS LIY ====================

    private fun refreshLinesList() {
        binding.llLinesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val categoriesInOrder = LinkedHashSet<String>()
        lines.forEach { categoriesInOrder.add(it.category) }

        for (category in categoriesInOrder) {
            val headerText = TextView(this).apply {
                text = category
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 14f
                setTextColor(android.graphics.Color.BLACK)
                setPadding(0, 16, 0, 4)
            }
            binding.llLinesContainer.addView(headerText)

            lines.forEachIndexed { index, line ->
                if (line.category != category) return@forEachIndexed

                val rowBinding = ItemFicheLineBinding.inflate(inflater, binding.llLinesContainer, false)
                rowBinding.tvLineTiraj.text = line.drawName
                rowBinding.tvLineBoul.text = line.numero
                rowBinding.tvLineOption.text = line.optionLabel ?: ""
                rowBinding.tvLineKob.text = moneyFormat.format(line.price)

                rowBinding.btnDeleteLine.setOnClickListener {
                    lines.removeAt(index)
                    refreshLinesList()
                    refreshTotal()
                }
                rowBinding.btnEditLinePrice.setOnClickListener {
                    promptForPrice { newPrice ->
                        line.price = newPrice
                        refreshLinesList()
                        refreshTotal()
                    }
                }

                binding.llLinesContainer.addView(rowBinding.root)
            }
        }
    }

    private fun refreshTotal() {
        val total = lines.sumOf { it.price }
        binding.tvTotal.text = "Total: ${moneyFormat.format(total)}"
    }

    // ==================== SOUMÈT + ENPRIME (AN PARALÈL) ====================

    private fun submitFiche() {
        val draw = selectedDraw
        val game = selectedGame

        if (draw == null) {
            Toast.makeText(this, "Chwazi yon tiraj anvan", Toast.LENGTH_SHORT).show()
            return
        }
        if (game == null) {
            Toast.makeText(this, "Pa gen jwèt disponib", Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "Ajoute omwen yon liy anvan w enprime", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceId = DeviceIdHelper.getDeviceId(this)
        // Yon SÈL nimewo fich pou TOUT liy sa yo — pèmèt backend lan
        // gwoupe yo ansanm nan "Mes fiches".
        val ficheNumber = generateFicheNumber()
        binding.btnPrint.isEnabled = false

        lifecycleScope.launch {
            val api = ApiClient.getService(applicationContext)
            val linesToSubmit = lines.toList()

            val results: List<Pair<FicheLine, String?>> = coroutineScope {
                linesToSubmit.map { line ->
                    async {
                        try {
                            val res = api.sellTicket(
                                SellTicketRequest(
                                    drawId = draw.id,
                                    gameId = game.id,
                                    numbers = listOf(line.numero),
                                    betAmount = line.price,
                                    posDeviceId = deviceId,
                                    ficheNumber = ficheNumber,
                                )
                            )
                            if (res.isSuccessful) {
                                line to null
                            } else {
                                line to (res.errorBody()?.string() ?: "erè enkoni")
                            }
                        } catch (e: Exception) {
                            line to (e.message ?: "erè rezo")
                        }
                    }
                }.awaitAll()
            }

            val successCount = results.count { it.second == null }
            val lastError = results.lastOrNull { it.second != null }
            val lastErrorMsg = lastError?.let { "Liy '${it.first.numero}': ${it.second}" }

            binding.btnPrint.isEnabled = true

            if (successCount == linesToSubmit.size) {
                printCurrentFiche(ficheNumber)
                lines.clear()
                binding.etBoulLa.text?.clear()
                binding.etMontant.text?.clear()
                refreshLinesList()
                refreshTotal()
            } else {
                Toast.makeText(
                    this@NewFicheActivity,
                    "$successCount/${linesToSubmit.size} liy pase. Dènye erè: $lastErrorMsg",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
