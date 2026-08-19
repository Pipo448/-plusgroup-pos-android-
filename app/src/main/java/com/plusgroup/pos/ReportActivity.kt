package com.plusgroup.pos

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityReportBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.EliminatedLine
import com.plusgroup.pos.printer.PrinterManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Rapò" — PARTIEL ak F.ELIMINER kounye a fonksyonèl. F.TIRAGE, F.GAGNANT,
 * ak TRASACT rete estòb ("byento") — chak youn ta bezwen pwòp lojik/wout
 * backend apa.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val moneyFormat = DecimalFormat("#,##0.00")
    private var selectedDate: Calendar = Calendar.getInstance()

    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }

    // Kisa mòd rapò kounye a — detèmine sa bouton "print" la enprime.
    private enum class ReportMode { PARTIEL, ELIMINE }
    private var currentMode = ReportMode.PARTIEL

    // Done ki dènyèman chaje pou "Fich Elimine", kenbe pou enpresyon an.
    private var lastEliminatedLines: List<EliminatedLine> = emptyList()
    private var lastEliminatedTotal = 0.0

    private var cachedCompanyName: String? = null
    private var cachedVendeur: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnPartiel.setOnClickListener {
            currentMode = ReportMode.PARTIEL
            loadPartialReport()
        }
        binding.btnFTirage.setOnClickListener { showComingSoon() }
        binding.btnFGagnant.setOnClickListener { showComingSoon() }
        binding.btnTrasact.setOnClickListener { showComingSoon() }
        binding.btnFEliminer.setOnClickListener {
            currentMode = ReportMode.ELIMINE
            loadEliminatedReport()
        }

        binding.btnPrintReport.setOnClickListener { printCurrentReport() }

        binding.tvDate.text = dateFormat.format(selectedDate.time)
        binding.tvDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate.set(year, month, day)
                    binding.tvDate.text = dateFormat.format(selectedDate.time)
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
            ).show()
        }
        binding.btnSearch.setOnClickListener {
            if (currentMode == ReportMode.ELIMINE) loadEliminatedReport() else loadPartialReport()
        }

        binding.tabBolet.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
        binding.tabParamet.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadPartialReport()
    }

    private fun showComingSoon() {
        Toast.makeText(this, "Byento", Toast.LENGTH_SHORT).show()
    }

    // ==================== RAPÒ PASYÈL ====================

    private fun loadPartialReport() {
        binding.llPartialResult.visibility = android.view.View.VISIBLE
        binding.llEliminatedResult.visibility = android.view.View.GONE

        val date = apiDateFormat.format(selectedDate.time)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getPartialReport(date)
                val report = res.body()?.data

                binding.tvTirageValue.text = report?.tirage ?: "—"
                binding.tvDateValue.text = report?.date ?: "—"
                binding.tvFicheVenduValue.text = "${report?.ficheVendu ?: 0}"
                binding.tvVenteValue.text = "${report?.vente ?: 0.0}"
                binding.tvCommissionValue.text = "${report?.commission ?: 0}"
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== F.ELIMINER ====================

    private fun loadEliminatedReport() {
        binding.llPartialResult.visibility = android.view.View.GONE
        binding.llEliminatedResult.visibility = android.view.View.VISIBLE

        val date = apiDateFormat.format(selectedDate.time)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getEliminatedReport(date)
                val report = res.body()?.data
                val lines = report?.lines ?: emptyList()

                lastEliminatedLines = lines
                lastEliminatedTotal = report?.totalVente ?: lines.sumOf { it.vente ?: 0.0 }

                binding.tvEliminatedSummary.text =
                    "${report?.ficheElimine ?: lines.size} fich elimine — Total: ${moneyFormat.format(lastEliminatedTotal)} HTG"

                renderEliminatedLines(lines)
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderEliminatedLines(lines: List<EliminatedLine>) {
        binding.llEliminatedContainer.removeAllViews()

        if (lines.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Pa gen fich elimine pou dat sa a."
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
                setTextColor(Color.DKGRAY)
            }
            binding.llEliminatedContainer.addView(empty)
            return
        }

        for (line in lines) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }
            card.addView(TextView(this).apply {
                text = "${line.drawName ?: "—"}  •  #${line.ficheNumber ?: line.ticketNumber ?: "—"}"
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 14f
            })
            card.addView(TextView(this).apply {
                text = "Vente: ${moneyFormat.format(line.vente ?: 0.0)}  /  Rezon: ${line.cancelReason ?: "—"}"
                textSize = 12f
                setTextColor(Color.DKGRAY)
            })
            card.addView(TextView(this).apply {
                text = formatDateTime(line.cancelledAt)
                textSize = 11f
                setTextColor(Color.GRAY)
            })
            binding.llEliminatedContainer.addView(card)

            val divider = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            binding.llEliminatedContainer.addView(divider)
        }
    }

    private fun formatDateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(raw.take(19))
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date!!)
        } catch (_: Exception) {
            raw
        }
    }

    // ==================== ENPRIME RAPÒ ====================

    private fun printCurrentReport() {
        when (currentMode) {
            ReportMode.ELIMINE -> printEliminatedReport()
            ReportMode.PARTIEL -> printPartialReport()
        }
    }

    private fun printPartialReport() {
        lifecycleScope.launch {
            ensureCompanyInfoCached()
            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(this@ReportActivity, "Enprimant pa konekte.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    printerManager.printFicheReceipt(
                        companyName = cachedCompanyName ?: "PLUS GROUP",
                        promoLine = "",
                        phone = "",
                        vendeur = cachedVendeur ?: "—",
                        dateTimeText = binding.tvDate.text.toString(),
                        ficheNumber = "RAPO PASYEL",
                        drawName = "Tout tiraj",
                        drawTotal = binding.tvVenteValue.text.toString(),
                        lines = listOf(
                            Triple("FV", "Fiche vendu", binding.tvFicheVenduValue.text.toString()),
                            Triple("CM", "Commission", binding.tvCommissionValue.text.toString()),
                        ),
                        grandTotal = binding.tvVenteValue.text.toString(),
                        footerMessage = "Rapò Pasyèl",
                        qrData = null,
                    )
                    Toast.makeText(this@ReportActivity, "Rapò voye bay enprimant lan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun printEliminatedReport() {
        if (lastEliminatedLines.isEmpty()) {
            Toast.makeText(this, "Pa gen fich elimine pou enprime", Toast.LENGTH_SHORT).show()
            return
        }
        val printLines = lastEliminatedLines.map { line ->
            Triple(
                "EL",
                line.ficheNumber ?: line.ticketNumber ?: "—",
                moneyFormat.format(line.vente ?: 0.0),
            )
        }

        lifecycleScope.launch {
            ensureCompanyInfoCached()
            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(this@ReportActivity, "Enprimant pa konekte.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    printerManager.printFicheReceipt(
                        companyName = cachedCompanyName ?: "PLUS GROUP",
                        promoLine = "",
                        phone = "",
                        vendeur = cachedVendeur ?: "—",
                        dateTimeText = binding.tvDate.text.toString(),
                        ficheNumber = "RAPO ELIMINE",
                        drawName = "Fich elimine — ${binding.tvDate.text}",
                        drawTotal = moneyFormat.format(lastEliminatedTotal),
                        lines = printLines,
                        grandTotal = moneyFormat.format(lastEliminatedTotal),
                        footerMessage = "Rapò Fich Elimine",
                        qrData = null,
                    )
                    Toast.makeText(this@ReportActivity, "Rapò voye bay enprimant lan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun ensureCompanyInfoCached() {
        if (cachedCompanyName != null) return
        try {
            val api = ApiClient.getService(applicationContext)
            val profile = api.getProfile().body()?.data
            cachedCompanyName = profile?.tenantName?.uppercase()
            cachedVendeur = profile?.branchName ?: profile?.fullName
        } catch (_: Exception) {
            // Kontinye ak valè default si sa echwe.
        }
    }
}
