package com.softlutionic.mobile.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChartPoint(val label: String, val value: BigDecimal)
data class RecentInvoice(val code: String, val date: String, val amount: BigDecimal?, val status: String)
data class DashboardSection(
 val cards: Map<String, BigDecimal>,
 val charts: Map<String, List<ChartPoint>>,
 val invoices: List<RecentInvoice>
)
data class DashboardData(val billing: DashboardSection, val commercial: DashboardSection) {
 companion object {
  fun parse(json: String): DashboardData {
   val root=JsonParser.parseString(json).asJsonObject
   return DashboardData(section(root.getAsJsonObject("facturacion")),section(root.getAsJsonObject("comercial")))
  }
  private fun section(root: JsonObject?): DashboardSection {
   val cards=root?.getAsJsonObject("cards")?.entrySet()?.mapNotNull { (key,value) ->
    runCatching { key to value.asBigDecimal }.getOrNull()
   }?.toMap().orEmpty()
   val charts=root?.getAsJsonObject("charts")?.entrySet()?.associate { (key,value) ->
    key to value.asJsonArray.mapNotNull { entry ->
     runCatching {
      val point=entry.asJsonObject
      ChartPoint(point.get("label")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),point["value"].asBigDecimal)
     }.getOrNull()
    }
   }.orEmpty()
   val invoices=root?.getAsJsonObject("tables")?.getAsJsonArray("facturas")?.map { value ->
    val row=value.asJsonObject
    fun text(key: String)=row[key]?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    RecentInvoice(text("CodigoFact"),text("Fecha_Emision"),runCatching { row["Monto_Total"].asBigDecimal }.getOrNull(),text("estado"))
   }?.sortedByDescending { it.date }?.take(6).orEmpty()
   return DashboardSection(cards,charts,invoices)
  }
 }
}

object FinancialFormat {
 fun money(value: BigDecimal?): String = value?.let {
  "C$"+NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits=2; maximumFractionDigits=2 }.format(it)
 } ?: "—"
 fun count(value: BigDecimal?): String = value?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) } ?: "—"
 fun date(value: String): String = runCatching {
  LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy",Locale("es","NI")))
 }.getOrDefault(value)
}
