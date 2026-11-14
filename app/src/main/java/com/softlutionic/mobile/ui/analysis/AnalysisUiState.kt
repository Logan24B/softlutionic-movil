package com.softlutionic.mobile.ui.analysis

import com.softlutionic.mobile.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

enum class AnalysisError { SESSION, RESTRICTED, UNAVAILABLE, NETWORK, CONTRACT }
data class AnalysisUiState(
    val filters: AnalyticsFilters = AnalyticsFilters(),
    val loading: Boolean = false,
    val data: AnalyticsData? = null,
    val error: AnalysisError? = null,
    val retryAfter: Long? = null,
    val loaded: Boolean = false
)

/** Separate lifecycle state; cancellation plus a generation guard reject stale responses. */
class AnalysisStore(private val scope: CoroutineScope, private val fetch: suspend (AnalyticsFilters) -> AnalyticsData) {
    private val mutable = MutableStateFlow(AnalysisUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    fun load(filters: AnalyticsFilters = state.value.filters) {
        val request = ++generation
        job?.cancel()
        mutable.value = AnalysisUiState(filters = filters, loading = true)
        job = scope.launch {
            try {
                val data = fetch(filters)
                if (request == generation) mutable.value = AnalysisUiState(filters, data = data, loaded = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (request == generation) {
                    val http = e as? HttpException
                    val error = when {
                        http?.code() == 401 -> AnalysisError.SESSION
                        http?.code() == 403 -> AnalysisError.RESTRICTED
                        http?.code() == 503 -> AnalysisError.UNAVAILABLE
                        e is java.io.IOException -> AnalysisError.NETWORK
                        else -> AnalysisError.CONTRACT
                    }
                    val retry = http?.response()?.headers()?.get("Retry-After")?.toLongOrNull()?.takeIf { it > 0 }
                    mutable.value = AnalysisUiState(filters, error = error, retryAfter = retry, loaded = true)
                }
            }
        }
    }
    fun clear() { ++generation; job?.cancel(); mutable.value = AnalysisUiState() }
}
