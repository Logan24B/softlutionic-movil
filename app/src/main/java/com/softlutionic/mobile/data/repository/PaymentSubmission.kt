package com.softlutionic.mobile.data.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.softlutionic.mobile.ui.invoices.InvoicePresentation as I

/** Durable unresolved operation prevents blind replays, including after process death. */
class PaymentSubmission(
    private val pending: suspend ()->JsonObject?,
    private val remember: suspend (JsonObject?)->Unit,
    private val list: suspend ()->List<JsonObject>,
    private val write: suspend (String?,JsonObject)->JsonObject
) {
    private val mutex=Mutex()
    suspend fun reconcile(): JsonObject?=mutex.withLock { reconcilePending() }
    private suspend fun reconcilePending(): JsonObject? {
        val unresolved=pending() ?: return null
        val target=I.text(unresolved,"_target")
        val saved=list().singleOrNull { candidate ->
            (target.isBlank() || I.text(candidate,"id")==target) &&
            unresolved.entrySet().filter { it.key!="_target" }.all { (key,value) -> candidate[key]==value ||
                (key in setOf("Monto_Pagado","Fecha_Pago") && equivalent(key,candidate[key]?.asString,value.asString)) }
        }
        if(saved!=null) remember(null)
        return saved
    }
    suspend fun submit(id: String?,payload: JsonObject): JsonObject=mutex.withLock {
        pending()?.let {
            val saved=reconcilePending()
            if(saved!=null) return@withLock saved
            error("Hay un pago sin confirmación. Revisa el historial y el recibo antes de realizar otra operación. No se reenviará automáticamente.")
        }
        val journal=payload.deepCopy().apply { if(id!=null) addProperty("_target",id) }
        remember(journal)
        try {
            val saved=write(id,payload)
            // Response is authoritative; a later cache failure must not turn success into failure.
            try { remember(null) } catch(_: Exception) { /* reconcile on next action */ }
            saved
        } catch(e: retrofit2.HttpException) {
            if(e.code() in listOf(400,401,403,404,409,422)) remember(null)
            throw e
        }
    }
    private fun equivalent(key: String,a: String?,b: String)=runCatching {
        if(key=="Monto_Pagado") a!!.toBigDecimal().compareTo(b.toBigDecimal())==0
        else java.time.OffsetDateTime.parse(a).toInstant()==java.time.OffsetDateTime.parse(b).toInstant()
    }.getOrDefault(false)
}
