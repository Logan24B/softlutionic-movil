package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.repository.PaymentSubmission
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PaymentSubmissionTest {
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private val payload=json("""{"FacturaId":4,"NumeroRecibo":31,"Monto_Pagado":"100.00","EstadoPago":true,"MetodoPago":false,"Fecha_Pago":"2026-10-28T10:00:00-06:00"}""")
    @Test fun timeoutNeverReportsSuccessOrRetries()=runBlocking {
        var journal: JsonObject?=null; var posts=0
        val submit=PaymentSubmission({journal},{journal=it},{emptyList()},{ _,_-> posts++; throw java.io.IOException("offline") })
        assertTrue(runCatching { submit.submit(null,payload) }.isFailure)
        assertNotNull(journal); assertTrue(runCatching { submit.submit(null,payload) }.isFailure); assertEquals(1,posts)
    }
    @Test fun processRestartReconcilesWithoutPost()=runBlocking {
        var journal: JsonObject?=payload.deepCopy(); var posts=0
        val saved=payload.deepCopy().apply { addProperty("id",88); addProperty("Fecha_Pago","2026-10-28T16:00:00Z") }
        val submit=PaymentSubmission({journal},{journal=it},{listOf(saved)},{ _,_-> posts++; saved })
        assertEquals(saved,submit.reconcile()); assertNull(journal); assertEquals(0,posts)
    }
    @Test fun successfulResponseSurvivesJournalCleanupFailure()=runBlocking {
        val submit=PaymentSubmission({null},{ if(it==null) throw java.io.IOException() },{emptyList()},{ _,_-> payload })
        assertEquals(payload,submit.submit(null,payload))
    }
    @Test fun anotherReceiptCannotResolvePendingOperation()=runBlocking {
        val other=payload.deepCopy().apply { addProperty("NumeroRecibo",99) }
        val submit=PaymentSubmission({payload},{},{listOf(other)},{ _,_-> error("must not write") })
        assertNull(submit.reconcile()); assertTrue(runCatching { submit.submit(null,payload) }.isFailure)
    }
    @Test fun editReconciliationRequiresSameTarget()=runBlocking {
        val pending=payload.deepCopy().apply { addProperty("_target","10") }
        val other=payload.deepCopy().apply { addProperty("id",20) }
        val submit=PaymentSubmission({pending},{},{listOf(other)},{ _,_-> error("must not write") })
        assertNull(submit.reconcile())
    }
}
