package com.plusgroup.pos

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.plusgroup.pos.databinding.ActivityMyFichesBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.FicheSummary
import com.plusgroup.pos.printer.PrinterManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Fich Mwen Yo" — istwa fich ajan an, ak filtè Debut/Fin.
 *
 * Chak KAT reprezante YON FICH KONPLÈ (pa yon sèl boul) — jan yon vrè
 * fich bolèt ta dwe parèt (10 boul nan menm fich la = 1 kat, pa 10 kat).
 * Klike sou yon kat louvri dyalòg detay ak 3 aksyon: REJOUER, IMPRIMER,
 * ELIMINÉ (jan modèl DVN Lotto a fè l).
 */
class MyFichesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyFichesBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val moneyFormat = DecimalFormat("#,##0.00")

    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()

    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }

    private var cachedCompanyName: String? = null
    private var cachedVendeur: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyFichesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.tvDebut.text = dateFormat.format(startDate.time)
        binding.tvFin.text = dateFormat.format(endDate.time)

        binding.tvDebut.setOnClickListener { pickDate(startDate) { binding.tvDebut.text = dateFormat.format(startDate.time) } }
        binding.tvFin.setOnClickListener { pickDate(endDate) { binding.tvFin.text = dateFormat.format(endDate.time) } }

        binding.btnSearch.setOnClickListener { loadFiches() }
        binding.swipeRefresh.setOnRefreshListener { loadFiches() }

        loadFiches()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun pickDate(target: Calendar, onPicked: () -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                target.set(year, month, day)
                onPicked()
            },
            target.get(Calendar.YEAR),
            target.get(Calendar.MONTH),
            target.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun loadFiches() {
        val start = apiDateFormat.format(startDate.time)
        val end = apiDateFormat.format(endDate.time)

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getMyTickets(start, end)
                // Fich ki ANILE pa dwe parèt ditou nan "Mes fiches" — sèl
                // kote yo dwe vizib se panèl admin (sekte "Elimine").
                val fiches = (res.body()?.data ?: emptyList()).filter { it.status != "cancelled" }
                renderFiches(fiches)
            } catch (e: Exception) {
                Toast.makeText(this@MyFichesActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    // ==================== AFICHAJ: yon kat pou chak fich ====================

    private fun renderFiches(fiches: List<FicheSummary>) {
        binding.llResultContainer.removeAllViews()

        if (fiches.isEmpty()) {
            addTotalRow(0.0)
            return
        }

        var grandTotal = 0.0

        for (fiche in fiches) {
            val mise = fiche.totalMise ?: 0.0
            grandTotal += mise

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { showFicheDetailDialog(fiche) }
            }

            val statusLabel = statusLabelFor(fiche.status)
            val titleView = TextView(this).apply {
                text = "${fiche.drawName ?: "—"}  (${formatDateTime(fiche.soldAt)})"
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 15f
            }
            val subtitleView = TextView(this).apply {
                text = "#tikè: ${maskTicketNumber(fiche.ticketNumber)}"
                textSize = 13f
                setTextColor(android.graphics.Color.DKGRAY)
            }
            val detailView = TextView(this).apply {
                text = "Mise: ${moneyFormat.format(mise)}  /  Gain: ${moneyFormat.format(fiche.totalGain ?: 0.0)}  /  $statusLabel"
                textSize = 13f
                setTextColor(android.graphics.Color.DKGRAY)
            }

            card.addView(titleView)
            card.addView(subtitleView)
            card.addView(detailView)
            binding.llResultContainer.addView(card)

            val divider = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2,
                )
                setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            }
            binding.llResultContainer.addView(divider)
        }

        addTotalRow(grandTotal)
    }

    private fun statusLabelFor(status: String?): String = when (status) {
        "active" -> "Aktif"
        "cancelled" -> "Anile"
        "winner" -> "Genyen"
        "lost" -> "Pèdi"
        "paid" -> "Peye"
        else -> status ?: "—"
    }

    private fun maskTicketNumber(ticketNumber: String?): String {
        if (ticketNumber.isNullOrBlank()) return "—"
        return if (ticketNumber.length > 5) "...${ticketNumber.takeLast(5)}...." else ticketNumber
    }

    private fun formatDateTime(soldAt: String?): String {
        if (soldAt.isNullOrBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(soldAt.take(19))
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date!!)
        } catch (_: Exception) {
            soldAt
        }
    }

    private fun addTotalRow(total: Double) {
        val totalView = TextView(this).apply {
            text = "TOTAL : ${moneyFormat.format(total)}"
            setTextColor(android.graphics.Color.parseColor("#8B0000"))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        binding.llResultContainer.addView(totalView, 0)
    }

    // ==================== DYALÒG DETAY (REJOUER / IMPRIMER / ELIMINÉ) ====================

    private fun showFicheDetailDialog(fiche: FicheSummary) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        container.addView(TextView(this).apply {
            text = fiche.drawName ?: "—"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 16f
            setPadding(0, 0, 0, 12)
        })

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply { text = "Jeux"; setTypeface(typeface, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(TextView(this).apply { text = "Pari"; setTypeface(typeface, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(TextView(this).apply { text = "Mise"; setTypeface(typeface, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        container.addView(header)

        fiche.lines?.forEach { line ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = categoryLabelFor(line.numero ?: ""); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply { text = line.numero ?: "—"; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply { text = moneyFormat.format(line.betAmount ?: 0.0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            container.addView(row)
        }

        container.addView(TextView(this).apply {
            text = "Total Mise: ${moneyFormat.format(fiche.totalMise ?: 0.0)}"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 0)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Detay Fich")
            .setView(container)
            .setNeutralButton("Fèmen", null)
            .create()

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Rejwe") { _, _ -> replayFiche(fiche) }
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Imprime") { _, _ -> printFicheSummary(fiche) }
        if (fiche.status == "active") {
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Elimine") { _, _ -> confirmCancelFiche(fiche) }
        }

        dialog.show()
    }

    private fun categoryLabelFor(numero: String): String {
        return when {
            numero.contains("*") -> "MARIAGE"
            numero.length == 2 -> "BORLETTE"
            numero.length == 3 -> "LOTO3"
            numero.length == 4 -> "LOTO4"
            numero.length == 5 -> "LOTO5"
            else -> "BORLETTE"
        }
    }

    // ==================== ELIMINE (tout liy fich la) ====================

    private fun confirmCancelFiche(fiche: FicheSummary) {
        AlertDialog.Builder(this)
            .setTitle("Konfime")
            .setMessage("Ou sèten ou vle anile fich sa a (tout ${fiche.lines?.size ?: 0} liy)? Aksyon sa a pa ka defèt.")
            .setPositiveButton("Wi, elimine l") { _, _ -> cancelFiche(fiche) }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun cancelFiche(fiche: FicheSummary) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = if (fiche.ficheNumber != null) {
                    api.cancelFiche(fiche.ficheNumber)
                } else {
                    val id = fiche.ticketIds?.firstOrNull()
                    if (id == null) {
                        Toast.makeText(this@MyFichesActivity, "Erè: ID manke", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    api.cancelTicket(id)
                }
                if (res.isSuccessful) {
                    Toast.makeText(this@MyFichesActivity, "Fich elimine avèk siksè", Toast.LENGTH_SHORT).show()
                    loadFiches()
                } else {
                    Toast.makeText(
                        this@MyFichesActivity,
                        "Erè: ${res.errorBody()?.string() ?: "pa t ka anile"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyFichesActivity, "Erè rezo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== IMPRIME (reenprime menm fich la egzakteman) ====================

    private fun printFicheSummary(fiche: FicheSummary) {
        val printLines = fiche.lines?.map { line ->
            Triple(
                shortCodeFor(categoryLabelFor(line.numero ?: "")),
                line.numero ?: "—",
                moneyFormat.format(line.betAmount ?: 0.0),
            )
        } ?: emptyList()

        lifecycleScope.launch {
            ensureCompanyInfoCached()

            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(this@MyFichesActivity, "Enprimant pa konekte.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    printerManager.printFicheReceipt(
                        companyName = cachedCompanyName ?: "PLUS GROUP",
                        promoLine = "",
                        phone = "",
                        vendeur = cachedVendeur ?: "—",
                        dateTimeText = formatDateTime(fiche.soldAt),
                        ficheNumber = fiche.ficheNumber ?: fiche.ticketNumber ?: "—",
                        drawName = fiche.drawName ?: "—",
                        drawTotal = moneyFormat.format(fiche.totalMise ?: 0.0),
                        lines = printLines,
                        grandTotal = moneyFormat.format(fiche.totalMise ?: 0.0),
                        footerMessage = "Kopi fich",
                        qrData = fiche.ficheNumber ?: fiche.ticketNumber,
                    )
                    Toast.makeText(this@MyFichesActivity, "Fich voye bay enprimant lan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shortCodeFor(category: String): String = when (category) {
        "BORLETTE" -> "BO"
        "LOTO3" -> "LT"
        "LOTO4" -> "L4"
        "LOTO5" -> "L5"
        "MARIAGE" -> "MA"
        else -> category.take(2)
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

    // ==================== REJWE (chaje boul yo editab nan Nouvèl Fich) ====================

    private fun replayFiche(fiche: FicheSummary) {
        val prefillLines = fiche.lines?.map {
            mapOf("numero" to (it.numero ?: ""), "price" to (it.betAmount ?: 0.0))
        } ?: emptyList()

        val intent = Intent(this, NewFicheActivity::class.java).apply {
            putExtra("PREFILL_DRAW_NAME", fiche.drawName)
            putExtra("PREFILL_LINES_JSON", Gson().toJson(prefillLines))
        }
        startActivity(intent)
    }
}
