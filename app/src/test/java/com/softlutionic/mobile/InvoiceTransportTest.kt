package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.remote.Api
import com.softlutionic.mobile.data.repository.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Exercises Retrofit/Gson against intercepted HTTP responses; no real financial writes. */
class InvoiceTransportTest {
    private val payload=JsonParser.parseString("""{"CodigoFact":77,"ContratoId":9,"Fecha_Emision":"2026-10-29","Hora_Emision":"00:05:00","lineas":[{"ServicioId":1,"Cantidad":2}]}""").asJsonObject
    private val saved=payload.deepCopy().apply { addProperty("id",10) }
    private fun api(status: Int,body: String)=Retrofit.Builder().baseUrl("https://fixture.invalid/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/api/mobile/v1/facturas/",chain.request().url.encodedPath)
            assertEquals("POST",chain.request().method)
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()).addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
    @Test fun actualRetrofit201DeserializesAsSuccessfulResponse()=runBlocking {
        val response=api(201,saved.toString()).createInvoice(payload)
        assertTrue(response.isSuccessful); assertEquals(201,response.code()); assertEquals(saved,response.body())
    }
    @Test fun validationAndPermissionAndConflictStatusesArePreserved()=runBlocking {
        for(status in listOf(400,403,409,422)) {
            var pending: JsonObject?=null; var posts=0
            val transport=api(status,"""{"detail":"fixture rejected"}""")
            val submit=InvoiceSubmission({ pending },{ pending=it },{ emptyList() },{ posts++; transport.createInvoice(it) },{}, {})
            try { submit.submit(payload); fail("Expected HTTP error") } catch(e: retrofit2.HttpException) { assertEquals(status,e.code()); assertTrue(e.response()!!.errorBody()!!.string().contains("fixture rejected")) }
            assertEquals(1,posts); assertNull(pending)
        }
    }
    @Test fun malformed201AfterCommitIsReconciledWithoutSecondPost()=runBlocking {
        var pending: JsonObject?=null; var posts=0
        val transport=api(201,"{broken")
        val submit=InvoiceSubmission({ pending },{ pending=it },{ if(posts>0) listOf(saved) else emptyList() },{ posts++; transport.createInvoice(it) },{}, {})
        assertTrue(submit.submit(payload).recovered); assertEquals(1,posts); assertNull(pending)
    }
}
