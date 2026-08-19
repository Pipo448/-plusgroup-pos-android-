package com.plusgroup.pos.network.models

data class EliminatedLine(
    val ticketNumber: String?,
    val ficheNumber: String?,
    val drawName: String?,
    val cancelledAt: String?,
    val vente: Double?,
    val cancelReason: String?,
)

data class EliminatedReport(
    val date: String?,
    val ficheElimine: Int?,
    val totalVente: Double?,
    val lines: List<EliminatedLine>?,
)
