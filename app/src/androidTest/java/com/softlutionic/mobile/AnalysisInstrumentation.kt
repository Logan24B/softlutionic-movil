package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.softlutionic.mobile.databinding.ViewAnalysisBinding
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.ui.analysis.*
import java.math.BigDecimal
import java.io.File

/** Render-only test: no MainActivity launch, session, HTTP or DW access. */
class AnalysisInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            runOnMainSync {
                for (scale in listOf(1f, 1.4f)) {
                    val config = Configuration(targetContext.resources.configuration).apply { screenWidthDp = 320; fontScale = scale }
                    val context = ContextThemeWrapper(targetContext.createConfigurationContext(config), R.style.Theme_Softlutionic)
                    val screen = ViewAnalysisBinding.inflate(LayoutInflater.from(context))
                    var applied: AnalyticsFilters? = null
                    var back = false
                    val controller = AnalysisController(AppCompatActivity(), screen, { applied = it }, { back = true })
                    val filters = AnalyticsFilters("2026-01-01", "2026-01-31")
                    val data = AnalyticsData(AnalyticsDto.keys.mapIndexed { index, key -> key to BigDecimal("1234567890.1234").multiply(BigDecimal(index + 1)) }.toMap(),
                        (1..7).map { AnalyticsService(it.toString(), "Servicio de prueba con un nombre extenso $it", BigDecimal(100 - it)) })
                    fun texts(v: View): List<TextView> = if (v is TextView) listOf(v) else if (v is ViewGroup)
                        (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
                    fun draw(name: String) {
                        val width = (320 * context.resources.displayMetrics.density).toInt()
                        val content = screen.root.getChildAt(0)
                        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        content.layout(0, 0, width, content.measuredHeight)
                        check(content.measuredHeight > 0)
                        val bitmap = Bitmap.createBitmap(width, content.measuredHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        screen.root.background?.let { it.setBounds(0, 0, width, content.measuredHeight); it.draw(canvas) }
                        content.draw(canvas)
                        val dir = File(targetContext.getExternalFilesDir("verification"), "analysis").apply { mkdirs() }
                        File(dir, "$name-$scale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    controller.render(AnalysisUiState(filters, data = data, loaded = true))
                    check(texts(screen.indicators).any { it.text.contains("1,234,567,890.1234") || it.text.contains("1.234.567.890,1234") })
                    check(texts(screen.indicators).count { it.text.contains("Servicio de prueba") } == 5)
                    draw("success")
                    texts(screen.indicators).single { it.text.toString().startsWith("Ver todos los servicios") }.performClick()
                    check(texts(screen.indicators).count { it.text.contains("Servicio de prueba") } == 7)
                    val inputs = texts(screen.filters).filterIsInstance<EditText>()
                    inputs[0].setText("2026-02-30")
                    texts(screen.filters).single { it.text.toString() == context.getString(R.string.apply) }.performClick()
                    check(applied == null && inputs[0].error != null)
                    inputs[0].setText("2026-01-01"); inputs[1].setText("2026-01-31")
                    texts(screen.filters).single { it.text.toString() == context.getString(R.string.apply) }.performClick()
                    check(applied == filters)
                    texts(screen.filters).single { it.text.toString() == context.getString(R.string.clear_filter) }.performClick()
                    check(applied == AnalyticsFilters() && inputs.all { it.text.isEmpty() })
                    controller.render(AnalysisUiState(filters, loading = true))
                    check(screen.loading.visibility == View.VISIBLE && screen.indicators.childCount == 0)
                    controller.render(AnalysisUiState(filters, error = AnalysisError.RESTRICTED, loaded = true))
                    check(screen.retry.visibility == View.GONE && screen.status.text.contains("Acceso restringido"))
                    controller.render(AnalysisUiState(filters, error = AnalysisError.UNAVAILABLE, retryAfter = 30, loaded = true))
                    check(screen.status.text.contains("30"))
                    screen.retry.performClick(); check(applied == filters)
                    draw("unavailable")
                    controller.render(AnalysisUiState(data = data.copy(services = emptyList()), loaded = true))
                    check(texts(screen.indicators).any { it.text.toString() == context.getString(R.string.analysis_empty) })
                    screen.back.performClick(); check(back)
                }
            }
            finish(-1, Bundle().apply { putString("result", "Analysis render/actions passed at 320dp and font scales 1.0/1.4; fixtures only, no HTTP") })
        } catch (error: Throwable) {
            finish(0, Bundle().apply { putString("error", error.stackTraceToString()) })
        }
    }
}
