package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.ui.analysis.*
import java.math.BigDecimal
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Fixtures are test-only; no session, network, Room or production credentials. */
class AnalyticsTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private val summary = """{"filters":{},"data":[
        {"indicador":"facturacion_total","importe":"9007199254740993.1234"},
        {"indicador":"monto_facturas_pagadas","importe":"1.2300"},
        {"indicador":"facturacion_pendiente","importe":"0"},
        {"indicador":"facturacion_vencida","importe":"-2.5678"}]}"""
    private val ranking = """{"filters":{},"data":[{"servicio_key":2,"servicio":null,"importe":"1.2345"},
        {"servicio_key":1,"servicio":"Servicio de prueba","importe":"10.0001"}]}"""
    private fun data() = AnalyticsDto.parse(json(summary), json(ranking), AnalyticsFilters())
    private fun fails(block: () -> Unit) {
        try { block(); fail("Expected invalid contract") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    @Test fun exactContractDecimalsAndRanking() {
        val data = data()
        assertEquals(BigDecimal("9007199254740993.1234"), data.amounts["facturacion_total"])
        assertEquals(BigDecimal("1.2300"), data.amounts["monto_facturas_pagadas"])
        assertEquals(BigDecimal("-2.5678"), data.amounts["facturacion_vencida"])
        assertEquals(listOf("1", "2"), data.services.map { it.key })
        assertNull(data.services.last().name)
    }
    @Test fun emptyRankingDoesNotInventServices() {
        assertTrue(AnalyticsDto.parse(json(summary), json("""{"filters":{},"data":[]}"""), AnalyticsFilters()).services.isEmpty())
    }
    @Test fun missingSummaryIsNotZero() {
        fails { AnalyticsDto.parse(json("""{"filters":{},"data":[]}"""), json(ranking), AnalyticsFilters()) }
    }
    @Test fun invalidOrNumericAmountsAreRejected() {
        fails { AnalyticsDto.parse(json(summary.replace("\"1.2300\"", "1.23")), json(ranking), AnalyticsFilters()) }
        fails { AnalyticsDto.parse(json(summary.replace("1.2300", "not-a-decimal")), json(ranking), AnalyticsFilters()) }
    }
    @Test fun duplicateIndicatorsAreRejected() {
        fails { AnalyticsDto.parse(json(summary.replace("facturacion_total", "monto_facturas_pagadas")), json(ranking), AnalyticsFilters()) }
    }
    @Test fun datesValidateStrictlyAndAllowSingleBounds() {
        assertEquals("2026-01-01", AnalyticsFilters(start="2026-01-01").start)
        assertEquals("2026-12-31", AnalyticsFilters(end="2026-12-31").end)
        for (bad in listOf("2026-1-01", "2026-02-30", "", "2026-01-01T00:00:00")) {
            try { AnalyticsFilters(start=bad); fail("Invalid date accepted") } catch (_: RuntimeException) {}
        }
        fails { AnalyticsFilters("2026-02-01", "2026-01-01") }
    }
    @Test fun responseMustMatchRequestedFilters() {
        fails { AnalyticsDto.parse(json(summary), json(ranking), AnalyticsFilters(start="2026-01-01")) }
        val filters = AnalyticsFilters("2026-01-01", "2026-01-31")
        val actual = """"filters":{"start":"2026-01-01","end":"2026-01-31"}"""
        assertEquals(4, AnalyticsDto.parse(json(summary.replace("\"filters\":{}", actual)),
            json(ranking.replace("\"filters\":{}", actual)), filters).amounts.size)
    }
    @Test fun retrofitPathsAndQueryNames() {
        for ((name, path) in listOf("analyticsSummary" to "resumen-financiero", "analyticsServices" to "ranking-servicios")) {
            val method = Api::class.java.methods.single { it.name == name }
            assertEquals("api/analytics/v1/$path/", requireNotNull(method.getAnnotation(retrofit2.http.GET::class.java)).value)
            val names = method.parameterAnnotations.flatMap { it.toList() }.filterIsInstance<retrofit2.http.Query>().map { it.value }
            assertEquals(listOf("start", "end"), names)
        }
    }
    @Test fun loadingSuccessAndClear() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<AnalyticsData>()
            val store = AnalysisStore(scope) { gate.await() }
            store.load()
            assertTrue(store.state.value.loading)
            assertNull(store.state.value.data)
            gate.complete(data())
            assertTrue(store.state.value.loaded)
            assertFalse(store.state.value.loading)
            assertNotNull(store.state.value.data)
            store.clear()
            assertEquals(AnalysisUiState(), store.state.value)
        } finally { scope.cancel() }
    }
    @Test fun newerFiltersWinEvenIfOldRequestIgnoresCancellation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val old = CompletableDeferred<AnalyticsData>()
            val store = AnalysisStore(scope) { filters ->
                if (filters.start == null) withContext(NonCancellable) { old.await() } else data()
            }
            store.load()
            val latest = AnalyticsFilters(start="2026-01-01")
            store.load(latest)
            assertEquals(latest, store.state.value.filters)
            old.complete(data())
            assertEquals(latest, store.state.value.filters)
            assertTrue(store.state.value.loaded)
        } finally { scope.cancel() }
    }
    @Test fun errorsNeverBecomeZeroAnd503KeepsRetryAfter() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            for ((code, expected) in listOf(401 to AnalysisError.SESSION, 403 to AnalysisError.RESTRICTED, 503 to AnalysisError.UNAVAILABLE)) {
                val raw = okhttp3.Response.Builder().request(okhttp3.Request.Builder().url("https://example.test/").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("test").header("Retry-After", "30").build()
                val store = AnalysisStore(scope) { throw HttpException(Response.error<Any>("{}".toResponseBody("application/json".toMediaType()), raw)) }
                store.load()
                assertEquals(expected, store.state.value.error)
                assertNull(store.state.value.data)
                assertFalse(store.state.value.loading)
                assertEquals(30L, store.state.value.retryAfter)
            }
            for ((exception, expected) in listOf(java.io.IOException("sensitive") to AnalysisError.NETWORK,
                    IllegalArgumentException("sensitive") to AnalysisError.CONTRACT)) {
                val store = AnalysisStore(scope) { throw exception }
                store.load()
                assertEquals(expected, store.state.value.error)
                assertNull(store.state.value.data)
            }
        } finally { scope.cancel() }
    }
}
