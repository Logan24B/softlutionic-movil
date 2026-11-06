package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class InvoiceSubmissionTest {
    private class Fixture {
        val body=JsonParser.parseString("""{"CodigoFact":77,"ContratoId":9,"Fecha_Emision":"2026-10-29","Hora_Emision":"00:05:00","lineas":[{"ServicioId":1,"Cantidad":2}]}""").asJsonObject
        val saved=body.deepCopy().apply { remove("lineas"); addProperty("id",10); addProperty("Monto_Total","2000.00") }
        var pending: JsonObject?=null
        val records=mutableListOf<JsonObject>()
        var posts=0; var reads=0; var refreshes=0; var cached: JsonObject?=null
        var failRead=false; var failRefresh=false; var failCache=false
        var send: suspend (JsonObject)->Response<JsonObject> = { Response.success(201,saved) }
        fun coordinator()=InvoiceSubmission(
            pending={ pending },remember={ pending=it },
            list={ reads++; if(failRead) throw IOException(); records.toList() },
            post={ posts++; send(it) },cache={ if(failCache) throw IOException(); cached=it },
            refresh={ refreshes++; if(failRefresh) throw IOException() })
    }
    private suspend fun uncertain(action: suspend ()->Unit): InvoiceUncertain {
        try { action(); fail("Expected uncertain result") } catch(e: InvoiceUncertain) { return e }
        error("unreachable")
    }
    @Test fun http201IsConfirmedAndCached()=runBlocking {
        val f=Fixture(); val result=f.coordinator().submit(f.body)
        assertEquals(f.saved,result.invoice); assertFalse(result.refreshFailed); assertEquals(1,f.posts); assertNull(f.pending); assertEquals(f.saved,f.cached)
    }
    @Test fun http200IsAlsoConfirmed()=runBlocking {
        val f=Fixture(); f.send={ Response.success(200,f.saved) }
        assertEquals(f.saved,f.coordinator().submit(f.body).invoice)
    }
    @Test fun refreshFailureAfter201DoesNotFailCreation()=runBlocking {
        val f=Fixture(); f.failRefresh=true
        assertTrue(f.coordinator().submit(f.body).refreshFailed); assertEquals(f.saved,f.cached); assertEquals(1,f.posts); assertNull(f.pending)
    }
    @Test fun cacheFailureAfter201DoesNotFailCreation()=runBlocking {
        val f=Fixture(); f.failCache=true
        assertEquals(f.saved,f.coordinator().submit(f.body).invoice); assertEquals(1,f.posts)
    }
    @Test fun timeoutAfterCommitRecoversFromExistingCollectionWithoutResending()=runBlocking {
        val f=Fixture(); f.send={ f.records.add(f.saved); throw SocketTimeoutException() }
        val result=f.coordinator().submit(f.body)
        assertTrue(result.recovered); assertEquals(1,f.posts); assertNull(f.pending)
    }
    @Test fun timeoutWithoutMatchNeverAutomaticallyRetries()=runBlocking {
        val f=Fixture(); f.send={ throw SocketTimeoutException() }
        assertTrue(uncertain { f.coordinator().submit(f.body) }.retryReady)
        assertEquals(1,f.posts); assertEquals(f.body,f.pending)
        uncertain { f.coordinator().submit(f.body) }; assertEquals(1,f.posts)
    }
    @Test fun explicitRetryRechecksAndRecoversLateCommit()=runBlocking {
        val f=Fixture(); f.pending=f.body.deepCopy(); f.records.add(f.saved)
        assertTrue(f.coordinator().submit(f.body,true).recovered); assertEquals(0,f.posts)
    }
    @Test fun explicitRetryOnlySendsAfterSuccessfulAbsenceCheck()=runBlocking {
        val f=Fixture(); f.pending=f.body.deepCopy()
        f.coordinator().submit(f.body,true); assertEquals(1,f.posts); assertEquals(1,f.reads)
    }
    @Test fun unavailableVerificationBlocksRetryAcrossCoordinatorRestart()=runBlocking {
        val f=Fixture(); f.pending=f.body.deepCopy(); f.failRead=true
        assertFalse(uncertain { f.coordinator().submit(f.body,true) }.retryReady); assertEquals(0,f.posts); assertNotNull(f.pending)
    }
    @Test fun duplicateCodeDoesNotSendPost()=runBlocking {
        val f=Fixture(); f.records.add(f.saved)
        uncertain { f.coordinator().submit(f.body) }; assertEquals(0,f.posts)
    }
    @Test fun ambiguousCodeDoesNotConfirmOrResend()=runBlocking {
        val f=Fixture(); f.pending=f.body.deepCopy(); f.records.add(f.saved); f.records.add(f.saved.deepCopy().apply { addProperty("id",11) })
        assertFalse(uncertain { f.coordinator().submit(f.body,true) }.retryReady); assertEquals(0,f.posts)
    }
    @Test fun changedPayloadCannotRetryPendingRequest()=runBlocking {
        val f=Fixture(); f.pending=f.body.deepCopy()
        assertFalse(uncertain { f.coordinator().submit(f.body.deepCopy().apply { addProperty("CodigoFact",78) },true) }.retryReady); assertEquals(0,f.posts)
    }
    @Test fun invalidSuccessfulBodyReconcilesInsteadOfResending()=runBlocking {
        val f=Fixture(); f.send={ f.records.add(f.saved); Response.success(201,JsonObject()) }
        assertTrue(f.coordinator().submit(f.body).recovered); assertEquals(1,f.posts)
    }
}
