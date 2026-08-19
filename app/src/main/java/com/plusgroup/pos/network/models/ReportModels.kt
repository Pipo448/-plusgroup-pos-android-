package com.plusgroup.pos.network.models

data class FinTirageReport(
    val tirage: String?,
    val dateFrom: String?,
    val dateTo: String?,
    val datePrinted: String?,
    val vendeur: String?,
    val succursal: String?,
    val ficheVendu: Int?,
    val ficheGagnant: Int?,
    val vente: Double?,
    val commission: Double?,
    val aPaye: Double?,
    val profitPerte: Double?,
    val depot: Any?,
    val retrait: Any?,
    val balance: Double?,
)

data class GagnantLine(
    val ticketNumber: String?,
    val ficheNumber: String?,
    val drawName: String?,
    val soldAt: String?,
    val vente: Double?,
    val prizeAmount: Double?,
    val paid: Boolean?,
)

data class GagnantReport(
    val dateFrom: String?,
    val dateTo: String?,
    val ficheGagnant: Int?,
    val totalPrize: Double?,
    val lines: List<GagnantLine>?,
)

data class TransactionLine(
    val date: String?,
    val type: String?,
    val montant: Double?,
)
