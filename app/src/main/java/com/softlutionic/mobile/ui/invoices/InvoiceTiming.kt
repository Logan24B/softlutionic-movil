package com.softlutionic.mobile.ui.invoices

import android.content.Context
import android.provider.Settings
import java.time.*
import java.time.format.DateTimeFormatter

data class InvoiceEmission(val captured: ZonedDateTime) {
    val date: String get()=captured.toLocalDate().toString()
    val time: String get()=captured.toLocalTime().withNano(0).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
data class InvoiceClockStatus(val automaticTime: Boolean,val automaticZone: Boolean,val validZone: Boolean) {
    val ready get()=automaticTime && automaticZone && validZone
}
interface InvoiceTimeSource {
    fun status(): InvoiceClockStatus
    fun capture(): InvoiceEmission
}
class DeviceInvoiceTime(private val context: Context): InvoiceTimeSource {
    override fun status()=InvoiceClockStatus(
        runCatching { Settings.Global.getInt(context.contentResolver,Settings.Global.AUTO_TIME,0)==1 }.getOrDefault(false),
        runCatching { Settings.Global.getInt(context.contentResolver,Settings.Global.AUTO_TIME_ZONE,0)==1 }.getOrDefault(false),
        runCatching { ZoneId.of(ZoneId.systemDefault().id); true }.getOrDefault(false))
    override fun capture()=InvoiceTiming.capture()
}
object InvoiceTiming {
    // Evaluated at each call: a device zone change while the form is open is respected.
    fun capture(clock: Clock=Clock.system(ZoneId.systemDefault()))=InvoiceEmission(ZonedDateTime.now(clock))
    // Existing web calculateDueDate and backend factura/services.py, not a new catalog field.
    fun dueDate(date: LocalDate,typeName: String)=date.plusDays(if(typeName.trim().lowercase(java.util.Locale.ROOT)=="servicio mensual") 30 else 0)
    val dueTime: LocalTime=LocalTime.of(23,59)
}
