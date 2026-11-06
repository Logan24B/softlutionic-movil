package com.softlutionic.mobile.ui

import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.softlutionic.mobile.R
import com.softlutionic.mobile.databinding.*
import java.math.BigDecimal

class DashboardRenderer(private val binding: ViewDashboardBinding,private val viewInvoices: (() -> Unit)?) {
 private var selectedMonth: String?=null
 private val context=binding.root.context
 private fun dp(n: Int)=(n*context.resources.displayMetrics.density).toInt()
 private fun color(id: Int)=ContextCompat.getColor(context,id)
 private fun text(value: String,large: Boolean=false)=TextView(context).apply {
  text=value; textSize=if(large) 20f else 14f
  setTextColor(color(if(large) R.color.login_text else R.color.login_muted))
  if(large) { typeface=Typeface.DEFAULT_BOLD; isAccessibilityHeading=true }
  setPadding(0,dp(8),0,dp(8))
 }
 private fun title(id: Int) { binding.panels.addView(text(context.getString(id),true)) }
 private data class Metric(val key: String,val label: Int,val icon: Int,val accent: Int,val money: Boolean=true)
 private val financial=listOf(
  Metric("total_facturado",R.string.total_billed,R.drawable.ic_invoice,R.color.login_accent),
  Metric("total_pagado",R.string.total_paid,R.drawable.ic_paid,R.color.dashboard_green),
  Metric("saldo_pendiente",R.string.balance_due,R.drawable.ic_pending,R.color.dashboard_amber),
  Metric("total_mora",R.string.total_late,R.drawable.login_info,R.color.login_error))
 private val operating=listOf(
  Metric("cantidad_clientes",R.string.client_count,R.drawable.login_user,R.color.login_cyan,false),
  Metric("cantidad_contratos",R.string.contract_count,R.drawable.ic_invoice,R.color.login_accent,false),
  Metric("ticket_promedio",R.string.average_ticket,R.drawable.ic_services,R.color.login_cyan),
  Metric("ingresos_por_servicio",R.string.service_income,R.drawable.ic_invoice,R.color.dashboard_green))
 private fun metrics(values: Map<String,BigDecimal>, definitions: List<Metric>,compact: Boolean=false) {
  // A single column at narrow widths or large font sizes preserves full currency values.
  val two=context.resources.configuration.screenWidthDp>=360 && context.resources.configuration.fontScale<=1.15f &&
   definitions.all { metric -> (if(metric.money) FinancialFormat.money(values[metric.key]) else FinancialFormat.count(values[metric.key])).length<=13 }
  definitions.chunked(if(two) 2 else 1).forEach { row ->
   val line=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
   row.forEach { metric ->
    val card=ItemKpiBinding.inflate(LayoutInflater.from(context),line,false)
    card.label.setText(metric.label); card.icon.setImageResource(metric.icon); card.icon.imageTintList=android.content.res.ColorStateList.valueOf(color(metric.accent))
    card.value.text=if(metric.money) FinancialFormat.money(values[metric.key]) else FinancialFormat.count(values[metric.key])
    card.value.textSize=if(compact) 21f else 24f
    card.root.layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }
    line.addView(card.root)
   }
   binding.panels.addView(line)
  }
 }
 private fun panel(): LinearLayout = LinearLayout(context).apply {
  orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)); setBackgroundResource(R.drawable.login_card)
  layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(16); bottomMargin=dp(8) }
  binding.panels.addView(this)
 }
 private fun chart(title: Int,points: List<ChartPoint>,donut: Boolean=false,money: Boolean=true) {
  val panel=panel(); panel.addView(text(context.getString(title),true))
  if(points.isEmpty()) { panel.addView(text(context.getString(R.string.no_series))); return }
  val diagonal=title==R.string.client_departments
  panel.addView(text(when(title) {
   R.string.client_departments -> "Clientes registrados por departamento · distribución general"
   R.string.monthly_billed -> "Importes mensuales devueltos para el período analizado"
   R.string.invoice_states -> "Cantidad de facturas por estado"
   else -> "Distribución según los datos disponibles"
  }))
  val graph=FinancialChartView(context,points,donut,money,diagonal)
  val labelWidth=android.graphics.Paint().apply { textSize=12f*context.resources.displayMetrics.density }.let { measure -> points.maxOf { measure.measureText(it.label) } }
  val chartHeight=if(diagonal) {
   val measure=android.graphics.Paint().apply { textSize=12f*context.resources.displayMetrics.density }
   dp(190)+(points.maxOf { measure.measureText(it.label) }*.866f).toInt()
  } else dp(210)
  // Horizontal scrolling keeps long time series legible instead of squeezing bars.
  if(!donut && (points.size>5 || diagonal)) {
   val scroll=HorizontalScrollView(context)
   scroll.addView(graph,ViewGroup.LayoutParams(maxOf(dp(points.size*72)+(if(diagonal) labelWidth*.5f else 0f).toInt(),context.resources.displayMetrics.widthPixels-dp(80)),chartHeight)); panel.addView(scroll)
  } else panel.addView(graph,LinearLayout.LayoutParams(-1,chartHeight))
  points.forEachIndexed { index,point ->
   panel.addView(text("●  ${point.label}   ${if(money) FinancialFormat.money(point.value) else FinancialFormat.count(point.value)}").apply { setTextColor(graph.pointColor(index)) })
  }
 }
 fun render(data: DashboardData?,commercial: Boolean) {
  binding.panels.removeAllViews()
  binding.empty.visibility=if(data==null) View.VISIBLE else View.GONE
  if(data==null) return
  if(!commercial) {
   title(R.string.billing_indicators)
   metrics(data.billing.cards,financial)
  }
  title(R.string.operating_metrics); metrics(data.commercial.cards,operating,true)
  if(commercial) {
   binding.panels.addView(text(context.getString(R.string.service_scope)))
   chart(R.string.service_income,data.commercial.charts["ingresos_por_servicio"].orEmpty())
   chart(R.string.contract_types,data.commercial.charts["contratos_por_tipo"].orEmpty(),true,false)
   chart(R.string.client_departments,data.commercial.charts["clientes_por_departamento"].orEmpty(),false,false)
  } else {
   // Filter only the aggregate series returned by the backend, never reconstruct totals from recent invoices.
   val months=data.billing.charts["facturado_por_mes"].orEmpty()
   if(selectedMonth!=null && months.none { it.label==selectedMonth }) selectedMonth=null
   val selector=MaterialButton(context).apply {
    isAllCaps=false
    text="Mes: "+(selectedMonth ?: "Todos los meses del período")
    setOnClickListener {
     val options=listOf("Todos los meses del período")+months.map { it.label }
     com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
      .setTitle("Mes a visualizar")
      .setSingleChoiceItems(options.toTypedArray(),selectedMonth?.let { months.indexOfFirst { point -> point.label==it }+1 } ?: 0) { dialog,index ->
       selectedMonth=if(index==0) null else months[index-1].label
       dialog.dismiss(); render(data,commercial)
      }.setNegativeButton(R.string.cancel,null).show()
    }
   }
   binding.panels.addView(selector)
   binding.panels.addView(text("El selector muestra los meses recibidos para el filtro de fechas activo."))
   chart(R.string.monthly_billed,months.filter { selectedMonth==null || it.label==selectedMonth })
   chart(R.string.invoice_states,data.billing.charts["facturas_por_estado"].orEmpty(),true,false)
   title(R.string.recent_invoices)
   if(data.billing.invoices.isEmpty()) binding.panels.addView(text(context.getString(R.string.no_invoices)))
   data.billing.invoices.forEach { invoice ->
    val row=panel()
    row.addView(text(context.getString(R.string.invoice_code,invoice.code),true))
    row.addView(text(FinancialFormat.date(invoice.date)))
    row.addView(text(FinancialFormat.money(invoice.amount),true))
    row.addView(text(invoice.status).apply {
     setTextColor(color(when {
      invoice.status.contains("pendiente",true) || invoice.status.contains("no pag",true) -> R.color.dashboard_amber
      invoice.status.contains("pagad",true) -> R.color.dashboard_green
      invoice.status.contains("mora",true) || invoice.status.contains("vencid",true) -> R.color.login_error
      else -> R.color.login_muted
     }))
     setBackgroundResource(R.drawable.login_status); setPadding(dp(12),dp(8),dp(12),dp(8))
     layoutParams=LinearLayout.LayoutParams(-2,-2)
    })
   }
   viewInvoices?.let { action -> binding.panels.addView(MaterialButton(context).apply {
    setText(R.string.see_all); minHeight=dp(48); setOnClickListener { action() }
   }) }
  }
 }
}
