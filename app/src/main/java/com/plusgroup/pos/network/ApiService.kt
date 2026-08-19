package com.plusgroup.pos.network

import com.plusgroup.pos.network.models.AgentBalance
import com.plusgroup.pos.network.models.AgentPrime
import com.plusgroup.pos.network.models.AgentProfile
import com.plusgroup.pos.network.models.ApiDataResponse
import com.plusgroup.pos.network.models.ApiListResponse
import com.plusgroup.pos.network.models.ApiMessageResponse
import com.plusgroup.pos.network.models.BlockedNumber
import com.plusgroup.pos.network.models.CompanySetting
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.EliminatedReport
import com.plusgroup.pos.network.models.FinTirageReport
import com.plusgroup.pos.network.models.GagnantReport
import com.plusgroup.pos.network.models.TransactionLine
import com.plusgroup.pos.network.models.FicheSummary
import com.plusgroup.pos.network.models.LotteryGame
import com.plusgroup.pos.network.models.LoginRequest
import com.plusgroup.pos.network.models.LoginResponse
import com.plusgroup.pos.network.models.PartialReport
import com.plusgroup.pos.network.models.RegisterDeviceRequest
import com.plusgroup.pos.network.models.SellTicketRequest
import com.plusgroup.pos.network.models.SellTicketResponse
import com.plusgroup.pos.network.models.Ticket
import com.plusgroup.pos.network.models.VerifyTicketResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("agent/register-device")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<ApiMessageResponse>

    @GET("agent/profile")
    suspend fun getProfile(): Response<ApiDataResponse<AgentProfile>>

    @GET("agent/balance")
    suspend fun getBalance(): Response<ApiDataResponse<AgentBalance>>

    @GET("agent/draws")
    suspend fun getDraws(@Query("status") status: String = "open"): Response<ApiListResponse<Draw>>

    @GET("agent/games")
    suspend fun getGames(): Response<ApiListResponse<LotteryGame>>

    @POST("agent/tickets")
    suspend fun sellTicket(@Body body: SellTicketRequest): Response<SellTicketResponse>

    // "Chache Fich" — chèche yon tikè pa nimewo (via SCAN oswa antre manyèl)
    @GET("agent/tickets/verify/{ticketNumber}")
    suspend fun verifyTicket(@Path("ticketNumber") ticketNumber: String): Response<ApiDataResponse<VerifyTicketResult>>

    // "Fich Mwen Yo" — kounye a retounen YON REZIME PA FICH (gwoupe pa
    // fiche_number backend a fè), pa yon liy pa boul.
    @GET("agent/tickets")
    suspend fun getMyTickets(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
    ): Response<ApiListResponse<FicheSummary>>

    // Detay yon sèl tikè pa ID (rete itil pou lòt kontèks, egzanp verifikasyon)
    @GET("agent/tickets/{id}")
    suspend fun getTicketById(@Path("id") id: String): Response<ApiDataResponse<Ticket>>

    // "Elimine" sou yon SÈL tikè (rete disponib, men "Mes fiches" itilize
    // pito cancelFiche() anba a pou anile TOUT liy yon fich yon sèl kou).
    @PATCH("agent/tickets/{id}/cancel")
    suspend fun cancelTicket(@Path("id") id: String): Response<ApiMessageResponse>

    // "Elimine" nan "Mes fiches" — anile TOUT liy ki fè pati fich la.
    @PATCH("agent/tickets/fiche/{ficheNumber}/cancel")
    suspend fun cancelFiche(@Path("ficheNumber") ficheNumber: String): Response<ApiMessageResponse>

    // "Rapò" — Rapò Pasyèl pou yon jou
    @GET("agent/reports/partial")
    suspend fun getPartialReport(@Query("date") date: String? = null): Response<ApiDataResponse<PartialReport>>

    // "Rapò" — F.ELIMINER : lis fich ajan an te elimine pou yon jou
    @GET("agent/reports/eliminated")
    suspend fun getEliminatedReport(@Query("date") date: String? = null): Response<ApiDataResponse<EliminatedReport>>

    // "Rapò" — F.TIRAGE : rapò rezime pou yon peryòd (De/A) ak balans
    @GET("agent/reports/fin-tirage")
    suspend fun getFinTirageReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("drawName") drawName: String? = null,
    ): Response<ApiDataResponse<FinTirageReport>>

    // "Rapò" — F.GAGNANT : lis fich ki genyen pou yon peryòd
    @GET("agent/reports/gagnant")
    suspend fun getGagnantReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("drawName") drawName: String? = null,
    ): Response<ApiDataResponse<GagnantReport>>

    // "Rapò" — TRASACT : istwa depo/retrè/ajisteman balans
    @GET("agent/reports/transactions")
    suspend fun getTransactionsReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Response<ApiListResponse<TransactionLine>>

    // "Boul Ki Soti" — nimewo ki soti pou tiraj ki gen rezilta deja antre
    @GET("agent/draws/results")
    suspend fun getDrawResults(@Query("date") date: String? = null): Response<ApiListResponse<com.plusgroup.pos.network.models.DrawResult>>

    // "Paramèt" — Prime pa ajan, boul bloke pa ajan, paramèt tenant antye
    @GET("agent/prime")
    suspend fun getPrime(): Response<ApiDataResponse<AgentPrime>>

    @GET("agent/blocked-numbers")
    suspend fun getBlockedNumbers(): Response<ApiListResponse<BlockedNumber>>

    @GET("agent/settings")
    suspend fun getCompanySettings(): Response<ApiListResponse<CompanySetting>>
}
