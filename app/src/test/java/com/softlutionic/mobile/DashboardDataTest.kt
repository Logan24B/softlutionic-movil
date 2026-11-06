package com.softlutionic.mobile

import com.softlutionic.mobile.ui.DashboardData
import com.softlutionic.mobile.ui.FinancialFormat
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DashboardDataTest {
 @Test fun preservesBackendAmountsSeriesAndInvoiceStates() {
  val data=DashboardData.parse("""{
   "facturacion": {
    "cards":{"total_facturado":12345678901234.56,"saldo_pendiente":-12.34},
    "charts":{"facturado_por_mes":[{"label":"2026-01","value":14.25},{"label":"2026-04","value":0}],"facturas_por_estado":[{"label":"Pagada","value":2}]},
    "tables":{"facturas":[{"CodigoFact":"A7","Fecha_Emision":"2026-01-10","Monto_Total":14.25,"estado":"Pagada"},{"CodigoFact":"A8","Fecha_Emision":"2026-04-01","Monto_Total":0,"estado":"Pendiente"}]}
   },
   "comercial":{"cards":{"cantidad_clientes":4,"ticket_promedio":14.25},"charts":{},"tables":{}}
  }""")
  assertEquals(BigDecimal("12345678901234.56"),data.billing.cards["total_facturado"])
  assertEquals(listOf("2026-01","2026-04"),data.billing.charts["facturado_por_mes"]!!.map { it.label })
  assertEquals(1,data.billing.charts["facturas_por_estado"]!!.size)
  assertEquals("A8",data.billing.invoices.first().code)
  assertEquals("Pendiente",data.billing.invoices.first().status)
  assertEquals("C$-12.34",FinancialFormat.money(data.billing.cards["saldo_pendiente"]))
 }
 @Test fun missingDataNeverBecomesInventedZero() {
  val data=DashboardData.parse("{}")
  assertTrue(data.billing.cards.isEmpty())
  assertTrue(data.billing.invoices.isEmpty())
  assertTrue(data.commercial.charts.isEmpty())
  assertEquals("—",FinancialFormat.money(data.billing.cards["total_facturado"]))
  assertEquals("C$0.00",FinancialFormat.money(BigDecimal.ZERO))
 }
 @Test fun formatsCurrencyAndCountsSeparately() {
  assertEquals("C$5,868.00",FinancialFormat.money(BigDecimal("5868")))
  assertEquals("1,234",FinancialFormat.count(BigDecimal("1234")))
  assertTrue(FinancialFormat.date("2026-10-22").contains("2026"))
 }
}
