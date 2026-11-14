package com.softlutionic.mobile.ui.analysis

import android.content.res.ColorStateList
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.databinding.ItemKpiBinding
import com.softlutionic.mobile.databinding.ViewAnalysisBinding
import com.softlutionic.mobile.ui.invoices.InvoiceViews
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Only renders verified API data; no database connection and no demo values. */
class AnalysisController(
    private val activity: AppCompatActivity, private val view: ViewAnalysisBinding,
    private val apply: (AnalyticsFilters) -> Unit, back: () -> Unit
) {
    private val ui = InvoiceViews(view.root.context)
    private val start = EditText(ui.ctx)
    private val end = EditText(ui.ctx)
    private var state = AnalysisUiState()
    private var expanded = false
    private val labels = listOf(R.string.analysis_total, R.string.analysis_paid, R.string.analysis_pending, R.string.analysis_overdue)
    private val colors = listOf(R.color.login_accent, R.color.dashboard_green, R.color.dashboard_amber, R.color.login_error)
    private fun text(id: Int) = view.root.context.getString(id)
    private fun visible(v: View, show: Boolean) { v.visibility = if (show) View.VISIBLE else View.GONE }
    private fun money(value: BigDecimal): String = NumberFormat.getNumberInstance(Locale("es", "NI")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = maxOf(2, value.scale())
    }.format(value)
    private fun bar(value: BigDecimal, maximum: BigDecimal, color: Int): ProgressBar =
        ProgressBar(ui.ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = if (maximum.signum() > 0) value.max(BigDecimal.ZERO).multiply(BigDecimal(1000))
                .divide(maximum, 0, RoundingMode.HALF_UP).toInt() else 0
            progressTintList = ColorStateList.valueOf(ContextCompat.getColor(ui.ctx, color))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(-1, ui.dp(14)).apply { bottomMargin = ui.dp(12) }
        }
    init {
        for ((input, hint) in listOf(start to R.string.analysis_start, end to R.string.analysis_end)) {
            input.setSingleLine(); input.inputType = android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_DATE
            view.filters.addView(ui.text(text(hint), 14f, true))
            input.hint = text(R.string.analysis_date_format)
            input.setTextColor(ContextCompat.getColor(ui.ctx, R.color.login_text))
            input.setHintTextColor(ContextCompat.getColor(ui.ctx, R.color.login_muted))
            input.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            view.filters.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        }
        view.filters.addView(ui.button(text(R.string.select_dates), false) {
            val builder = MaterialDatePicker.Builder.dateRangePicker().setTheme(R.style.ThemeOverlay_Softlutionic_Calendar).setTitleText(R.string.filter_period).setPositiveButtonText(R.string.apply).setNegativeButtonText(R.string.cancel)
            runCatching {
                val from = LocalDate.parse(start.text.toString()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val to = LocalDate.parse(end.text.toString()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                builder.setSelection(androidx.core.util.Pair(from, to))
            }
            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { range ->
                fun iso(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate().toString()
                start.setText(iso(range.first)); end.setText(iso(range.second))
            }
            picker.show(activity.supportFragmentManager, "analysis_dates")
        })
        view.filters.addView(ui.button(text(R.string.apply)) {
            val filters = runCatching { AnalyticsFilters(start.text.toString().trim().ifEmpty { null }, end.text.toString().trim().ifEmpty { null }) }.getOrNull()
            if (filters == null) { start.error = text(R.string.analysis_invalid_dates); end.error = text(R.string.analysis_invalid_dates) }
            else { start.error = null; end.error = null; apply(filters) }
        })
        view.filters.addView(ui.button(text(R.string.clear_filter), false) {
            start.setText(""); end.setText(""); start.error = null; end.error = null; apply(AnalyticsFilters())
        })
        view.retry.setOnClickListener { apply(state.filters) }
        view.back.setOnClickListener { back() }
    }
    fun render(next: AnalysisUiState) {
        val changed = next.filters != state.filters
        state = next
        if (changed) expanded = false
        if (!start.hasFocus() && !end.hasFocus()) {
            start.setText(next.filters.start.orEmpty()); end.setText(next.filters.end.orEmpty())
        }
        view.retry.setText(if (next.error == null) R.string.refresh else R.string.retry)
        visible(view.loading, next.loading)
        visible(view.retry, next.loaded && !next.loading && next.error != AnalysisError.RESTRICTED && next.error != AnalysisError.SESSION)
        val error = when (next.error) {
            AnalysisError.SESSION -> R.string.analysis_session
            AnalysisError.RESTRICTED -> R.string.analysis_restricted
            AnalysisError.UNAVAILABLE -> R.string.analysis_unavailable
            AnalysisError.NETWORK -> R.string.analysis_network
            AnalysisError.CONTRACT -> R.string.analysis_contract
            null -> null
        }
        val period = if (next.filters == AnalyticsFilters()) text(R.string.analysis_all) else
            (next.filters.start ?: text(R.string.analysis_open_start)) + " → " + (next.filters.end ?: text(R.string.analysis_open_end))
        view.status.text = when {
            next.loading -> text(R.string.analysis_loading)
            error != null -> text(error) + (next.retryAfter?.let { " " + view.root.context.getString(R.string.analysis_retry_after, it) } ?: "")
            else -> view.root.context.getString(R.string.analysis_period, period)
        }
        view.indicators.removeAllViews()
        val data = next.data ?: return
        AnalyticsDto.keys.forEachIndexed { index, key ->
            val card = ItemKpiBinding.inflate(android.view.LayoutInflater.from(ui.ctx), view.indicators, false)
            card.label.setText(labels[index]); card.value.text = money(data.amounts.getValue(key))
            card.value.setTextColor(ContextCompat.getColor(ui.ctx, colors[index]))
            card.icon.visibility = View.GONE
            view.indicators.addView(card.root)
        }
        view.indicators.addView(ui.text(text(R.string.analysis_note), 14f, true))
        if (data.amounts.values.all { it.signum() == 0 }) view.indicators.addView(ui.text(text(R.string.analysis_zero), 14f, true))
        view.indicators.addView(ui.text(text(R.string.analysis_comparison), 20f))
        val maximumState = AnalyticsDto.keys.drop(1).maxOf { data.amounts.getValue(it).max(BigDecimal.ZERO) }
        AnalyticsDto.keys.drop(1).forEachIndexed { i, key ->
            val value = data.amounts.getValue(key)
            view.indicators.addView(ui.text(text(labels[i + 1]) + ": " + money(value), 15f).apply {
                setTextColor(ContextCompat.getColor(ui.ctx, colors[i + 1]))
            })
            view.indicators.addView(bar(value, maximumState, colors[i + 1]))
        }
        if (data.amounts.values.any { it.signum() < 0 } || data.services.any { it.amount.signum() < 0 })
            view.indicators.addView(ui.text(text(R.string.analysis_negative), 14f, true))
        view.indicators.addView(ui.text(text(R.string.analysis_ranking), 20f))
        if (data.services.isEmpty()) view.indicators.addView(ui.text(text(R.string.analysis_empty), 14f, true))
        val maximum = data.services.maxOfOrNull { it.amount.max(BigDecimal.ZERO) } ?: BigDecimal.ZERO
        (if (expanded) data.services else data.services.take(5)).forEachIndexed { index, service ->
            val body = ui.column()
            body.addView(ui.text(view.root.context.getString(R.string.analysis_rank_row, index + 1,
                service.name?.takeIf { it.isNotBlank() } ?: text(R.string.analysis_no_name), money(service.amount)), 15f))
            body.addView(bar(service.amount, maximum, if (index % 2 == 0) R.color.login_accent else R.color.login_cyan))
            view.indicators.addView(ui.card().apply { addView(body) })
        }
        if (data.services.size > 5) view.indicators.addView(ui.button(if (expanded) text(R.string.analysis_less)
            else view.root.context.getString(R.string.analysis_more, data.services.size), false) {
                expanded = !expanded; render(state)
            })
    }
}
