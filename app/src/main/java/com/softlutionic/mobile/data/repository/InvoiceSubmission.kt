package com.softlutionic.mobile.data.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

data class InvoiceReceipt(val invoice: JsonObject,val refreshFailed: Boolean=false,val recovered: Boolean=false)
class InvoiceUncertain(message: String,val retryReady: Boolean=false): Exception(message)

/** A persisted pending request is reconciled before any explicitly requested retry. */
class InvoiceSubmission(
    private val pending: suspend ()->JsonObject?,
    private val remember: suspend (JsonObject?)->Unit,
    private val list: suspend ()->List<JsonObject>,
    private val post: suspend (JsonObject)->Response<JsonObject>,
    private val cache: suspend (JsonObject)->Unit,
    private val refresh: suspend ()->Unit,
    private val log: (String)->Unit = {}
) {
    private val mutex=Mutex()
    suspend fun submit(payload: JsonObject,allowRetry: Boolean=false): InvoiceReceipt=mutex.withLock {
        val old=pending()
        if(old!=null) {
            val found=reconcile(old)
            if(found!=null) return@withLock confirmed(found,true)
            if(old!=payload || !allowRetry) throw InvoiceUncertain("No se encontró la factura pendiente al consultar el servidor. Revisa el listado y pulsa Reintentar generación para autorizar un nuevo intento con los mismos datos.",old==payload)
        } else {
            // Existing collection endpoint, no assumed filtering or new route.
            if(list().any { it["CodigoFact"]==payload["CodigoFact"] })
                throw InvoiceUncertain("Ya existe una factura con ese código. Revisa el listado antes de generar otra.")
        }
        remember(payload.deepCopy()) // Persist before sending; survives app/process restarts.
        log("POST start CodigoFact=${payload["CodigoFact"]}")
        val response=try { post(payload) } catch(e: Exception) {
            if(e is CancellationException) throw e
            log("POST uncertain exception=${e.javaClass.simpleName}")
            val found=reconcile(payload)
            if(found!=null) return@withLock confirmed(found,true)
            throw InvoiceUncertain("No se recibió confirmación y no se encontró la factura al consultar el servidor. Revisa el listado antes de reintentar; no se reenvió el POST.",true)
        }
        log("POST status=${response.code()} body=${response.body()!=null || response.errorBody()!=null}")
        if(!response.isSuccessful) {
            if(response.code() in 400..499) remember(null)
            throw retrofit2.HttpException(response)
        }
        val saved=response.body()
        if(saved==null || !saved.has("id") || saved["id"].isJsonNull || saved["CodigoFact"]!=payload["CodigoFact"]) {
            log("POST deserialization=invalid")
            val found=reconcile(payload)
            if(found!=null) return@withLock confirmed(found,true)
            throw InvoiceUncertain("El servidor respondió sin una factura válida. No se reenvió la solicitud; verifica el listado antes de reintentar.",true)
        }
        log("POST deserialization=success")
        confirmed(saved,false)
    }
    private suspend fun reconcile(request: JsonObject): JsonObject? {
        log("reconcile start CodigoFact=${request["CodigoFact"]}")
        val matches=try { list().filter { it["CodigoFact"]==request["CodigoFact"] } } catch(e: Exception) {
            if(e is CancellationException) throw e
            log("reconcile failed exception=${e.javaClass.simpleName}")
            throw InvoiceUncertain("No se pudo comprobar si la factura fue creada. El envío permanece pendiente; vuelve a comprobar cuando tengas conexión. No se reenvió el POST.")
        }
        if(matches.isEmpty()) { log("reconcile absent"); return null }
        val found=matches.singleOrNull()
        if(found==null || !found.has("id") || listOf("ContratoId","Fecha_Emision","Hora_Emision").any { found[it]!=request[it] })
            throw InvoiceUncertain("El código coincide con otra factura o con varios registros. Revisa el listado; no se reenvió el POST.")
        log("reconcile confirmed")
        return found
    }
    private suspend fun confirmed(saved: JsonObject,recovered: Boolean): InvoiceReceipt {
        var failed=false
        try { cache(saved) } catch(e: Exception) { if(e is CancellationException) throw e; failed=true; log("cache failed exception=${e.javaClass.simpleName}") }
        try { remember(null) } catch(e: Exception) { if(e is CancellationException) throw e; failed=true }
        log("refresh start")
        try { refresh(); log("refresh success") } catch(e: Exception) { if(e is CancellationException) throw e; failed=true; log("refresh failed exception=${e.javaClass.simpleName}") }
        return InvoiceReceipt(saved,failed,recovered)
    }
}
