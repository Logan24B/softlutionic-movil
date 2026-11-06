package com.softlutionic.mobile.ui.invoices

import android.content.res.ColorStateList
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.*
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.*
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import java.time.*

/** Specialized invoice workflow; no networking and no independent detail POSTs. */
class InvoiceSheets(private val host: AppCompatActivity,private val vm: MainViewModel,private val module: ()->Module) {
    private val ui=InvoiceViews(host)
    private var active: BottomSheetDialog?=null
    private var chooser: BottomSheetDialog?=null
    private var update: (() -> Unit)?=null
    private var updateCatalogs: (() -> Unit)?=null
    private var clockDialog: androidx.appcompat.app.AlertDialog?=null
    private val clockHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var clockTick: Runnable?=null
    private var clockObserver: androidx.lifecycle.LifecycleEventObserver?=null
    private fun stopClock() { clockTick?.let { clockHandler.removeCallbacks(it) }; clockTick=null; clockObserver?.let { host.lifecycle.removeObserver(it) }; clockObserver=null }
    fun dismiss() { stopClock(); clockDialog?.dismiss(); clockDialog=null; chooser?.dismiss(); chooser=null; active?.dismiss(); active=null; update=null; updateCatalogs=null }
    fun stateChanged() {
        updateCatalogs?.invoke(); update?.invoke()
        if(vm.invoiceClockWarning.value && active?.isShowing==true && clockDialog?.isShowing!=true) {
            vm.invoiceClockWarning.value=false
            clockDialog=MaterialAlertDialogBuilder(ui.ctx).setTitle("Verifica la fecha y hora del dispositivo")
                .setMessage("Para registrar correctamente la emisión de la factura, activa la fecha, hora y zona horaria automáticas del dispositivo.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Abrir configuración") { _,_ ->
                    runCatching { host.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)) }
                        .onFailure { MaterialAlertDialogBuilder(ui.ctx).setMessage("Abre los ajustes de fecha y hora del dispositivo.").setPositiveButton("Cerrar",null).show() }
                }.show()
        }
    }
    private fun startClock(preview: ()->Unit) {
        val owner=active
        val runnable=object: Runnable { override fun run() {
            if(active!==owner || owner?.isShowing!=true) return
            preview(); clockHandler.postDelayed(this,1000)
        } }
        clockTick=runnable
        clockObserver=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_START) { clockHandler.removeCallbacks(runnable); clockHandler.post(runnable) }
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) clockHandler.removeCallbacks(runnable)
        }.also { host.lifecycle.addObserver(it) }
    }
    private fun catalogs()=vm.invoiceCatalogs.value
    private fun show(body: LinearLayout,onUpdate: (() -> Unit)?=null) {
        dismiss(); body.setBackgroundColor(ui.color(R.color.login_bg)); update=onUpdate
        val dialog=BottomSheetDialog(ui.ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet)
        active=dialog
        dialog.apply {
            val scroll=androidx.core.widget.NestedScrollView(context).apply { isFillViewport=true; addView(body) }
            setContentView(scroll); window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setOnDismissListener { if(active===dialog) { stopClock(); active=null; update=null; updateCatalogs=null } }
            show(); behavior.skipCollapsed=true
            findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { it.layoutParams=it.layoutParams.apply { height=ViewGroup.LayoutParams.MATCH_PARENT } }
            behavior.state=BottomSheetBehavior.STATE_EXPANDED
        }
        update?.invoke()
    }
    private fun fieldLabel(body: LinearLayout,label: String,value: String) {
        body.addView(ui.text(label.uppercase(),12f,true)); body.addView(ui.text(value.ifBlank { "No disponible" }))
    }
    private fun pickDate(title: String,value: String,chosen: (String)->Unit) {
        val builder=MaterialDatePicker.Builder.datePicker().setTheme(R.style.ThemeOverlay_Softlutionic_Calendar).setTitleText(title)
        runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()?.let { builder.setSelection(it) }
        val picker=builder.build()
        picker.addOnPositiveButtonClickListener { chosen(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
        picker.show(host.supportFragmentManager,"invoice-date")
    }
    private fun pickTime(title: String,value: String,chosen: (String)->Unit) {
        val old=runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.NOON)
        val picker=MaterialTimePicker.Builder().setTheme(R.style.ThemeOverlay_Softlutionic_InvoiceTimePicker).setTimeFormat(TimeFormat.CLOCK_12H).setTitleText(title).setHour(old.hour).setMinute(old.minute).build()
        picker.addOnPositiveButtonClickListener { chosen(LocalTime.of(picker.hour,picker.minute).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))) }
        picker.show(host.supportFragmentManager,"invoice-time")
    }
    fun detail(data: JsonObject) {
        val row=InvoicePresentation.row(data,catalogs()); val body=ui.column()
        body.addView(ui.text(if(row.code.isBlank()) "FACTURA" else "FACTURA ${row.code}",26f))
        body.addView(ui.badge(row.state),LinearLayout.LayoutParams(-2,-2))
        fieldLabel(body,"Cliente",row.client); fieldLabel(body,"Contrato",row.contract)
        fieldLabel(body,"Emisión",listOf(InvoicePresentation.date(InvoicePresentation.text(data,"Fecha_Emision")),InvoicePresentation.time(InvoicePresentation.text(data,"Hora_Emision"))).filter { it.isNotBlank() }.joinToString(" · "))
        fieldLabel(body,"Vencimiento",listOf(InvoicePresentation.date(InvoicePresentation.text(data,"Fecha_Vencimiento")),InvoicePresentation.time(InvoicePresentation.text(data,"Hora_Vencimiento"))).filter { it.isNotBlank() }.joinToString(" · "))
        body.addView(ui.text("SERVICIOS",14f,true))
        val lines=InvoicePresentation.array(data,"detalles")
        if(lines.isEmpty()) body.addView(ui.text("El detalle de servicios no está disponible en los datos guardados.",14f,true))
        lines.forEach { line ->
            val service=InvoicePresentation.nested(line,"servicio_detalle") ?: InvoicePresentation.byId(catalogs(),"servicios",InvoicePresentation.text(line,"ServicioId"))
            val content=ui.column(); content.addView(ui.text(InvoicePresentation.text(service,"Nombre_Servicio").ifBlank { "Servicio no disponible" },18f))
            content.addView(ui.text("${InvoicePresentation.text(line,"Cantidad")} × ${InvoicePresentation.money(InvoicePresentation.decimal(line,"PrecioVenta"))}",14f,true))
            content.addView(ui.text(InvoicePresentation.money(InvoicePresentation.decimal(line,"Subtotal")),20f))
            body.addView(ui.card().apply { addView(content) })
        }
        body.addView(ui.text("TOTAL",14f,true)); body.addView(ui.text(InvoicePresentation.money(InvoicePresentation.decimal(data,"Monto_Total")),30f).apply { setTextColor(ui.color(R.color.login_cyan)) })
        val user=InvoicePresentation.nested(data,"usuario_detalle")
        val userName=listOf(InvoicePresentation.text(user,"PrimerNombre"),InvoicePresentation.text(user,"SegundoNombre")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { InvoicePresentation.text(user,"Usuario") }
        if(userName.isNotBlank()) fieldLabel(body,"Registrada por",userName)
        val edit=ui.button("Editar factura") { if(!vm.invoiceSaving.value && module().permissions.update) { vm.beginInvoice(data); form() } }
        val delete=ui.button("Eliminar factura",false) {
            if(vm.invoiceSaving.value || !module().permissions.delete) return@button
            MaterialAlertDialogBuilder(ui.ctx).setTitle("¿Eliminar factura?").setMessage("Esta acción puede afectar registros relacionados.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar") { _,_ ->
                    if(!vm.invoiceSaving.value && module().permissions.delete) vm.delete("facturas",row.id,success={ dismiss() },failure={ error ->
                        MaterialAlertDialogBuilder(ui.ctx).setMessage(if(error is retrofit2.HttpException && error.code()==409) "No se puede eliminar porque tiene registros relacionados." else "No se pudo eliminar la factura.").setPositiveButton("Cerrar",null).show()
                    })
                }.show()
        }
        val close=ui.button("Cerrar",false) { dismiss() }
        body.addView(edit); body.addView(delete); body.addView(close)
        show(body) {
            edit.visibility=if(module().permissions.update) View.VISIBLE else View.GONE
            delete.visibility=if(module().permissions.delete) View.VISIBLE else View.GONE
            listOf(edit,delete,close).forEach { it.isEnabled=!vm.invoiceSaving.value }; active?.setCancelable(!vm.invoiceSaving.value)
        }
    }
    fun filters(rows: List<InvoiceRow>) {
        val body=ui.column(); body.addView(ui.text("Filtrar facturas",23f))
        val old=vm.invoiceFilters.value; var state=old.state; var client=old.clientId; var start=old.start; var end=old.end
        fun selector(label: String,options: List<Pair<String,String>>,selected: String?,select: (String?)->Unit) {
            val input=ClientDropdownBinding.inflate(LayoutInflater.from(ui.ctx),body,false); input.root.hint=label
            val choices=listOf("" to "Todos")+options
            input.input.setAdapter(ArrayAdapter(ui.ctx,R.layout.client_dropdown_item,choices.map { it.second }))
            input.input.setText(choices.find { it.first==selected }?.second ?: "Todos",false)
            input.input.setOnItemClickListener { _,_,index,_ -> select(choices[index].first.ifBlank { null }) }; body.addView(input.root)
        }
        selector("Estado",(catalogs()["estados-factura"].orEmpty().map { InvoicePresentation.text(it,"estado") }+rows.map { it.state }).filter { it.isNotBlank() && it!="Estado no disponible" }.distinct().map { it to it },state) { state=it }
        selector("Cliente",rows.filter { it.clientId.isNotBlank() }.distinctBy { it.clientId }.map { it.clientId to it.client },client) { client=it }
        val from=ui.button(start?.let { "Desde: ${InvoicePresentation.date(it)}" } ?: "Desde",false) {}
        val to=ui.button(end?.let { "Hasta: ${InvoicePresentation.date(it)}" } ?: "Hasta",false) {}
        from.setOnClickListener { pickDate("Emitidas desde",start.orEmpty()) { start=it; from.text="Desde: ${InvoicePresentation.date(it)}" } }
        to.setOnClickListener { pickDate("Emitidas hasta",end.orEmpty()) { end=it; to.text="Hasta: ${InvoicePresentation.date(it)}" } }
        body.addView(from); body.addView(to)
        val error=ui.text("",14f).apply { setTextColor(ui.color(R.color.login_error)) }; body.addView(error)
        body.addView(ui.button("Aplicar filtros") {
            if(start!=null && end!=null && start!!>end!!) { error.text="Selecciona un rango de fechas válido."; return@button }
            vm.invoiceFilters.value=old.copy(state=state,clientId=client,start=start,end=end); dismiss()
        })
        body.addView(ui.button("Limpiar filtros",false) { vm.invoiceFilters.value=InvoiceFilters(query=old.query); dismiss() }); show(body)
    }
    fun form() {
        val draft=vm.invoiceDraft.value ?: return
        if(!(if(draft.id==null) module().permissions.create else module().permissions.update)) return
        val body=ui.column()
        body.addView(ui.text(if(draft.id==null) "Nueva factura" else "Editar factura",25f))
        body.addView(ui.text("Registra los servicios facturados a un cliente.",14f,true))
        body.addView(ui.text("INFORMACIÓN DE FACTURA",14f,true))
        val controls=mutableListOf<View>(); val layouts=linkedMapOf<String,TextInputLayout>()
        val partyUpdates=mutableListOf<()->Unit>()
        fun party(key: String,route: String,label: String) {
            val input=ClientDropdownBinding.inflate(LayoutInflater.from(ui.ctx),body,false)
            input.root.hint=label
            input.root.endIconMode=TextInputLayout.END_ICON_CUSTOM
            input.root.setEndIconDrawable(android.R.drawable.arrow_down_float)
            input.input.isFocusable=false
            fun choose() {
                if(vm.invoiceSaving.value) return
                chooseParty(route) { selected ->
                    val current=vm.invoiceDraft.value ?: return@chooseParty
                    vm.invoiceDraft.value=if(route=="clientes") current.copy(clientId=InvoicePresentation.text(selected,"id"),values=current.values-"ContratoId")
                        else current.copy(typeId=InvoicePresentation.text(selected,"id"),values=current.values-"ContratoId")
                    vm.invoiceErrors.value=vm.invoiceErrors.value-key-"ContratoId"; stateChanged()
                }
            }
            input.input.setOnClickListener { choose() }; input.root.setEndIconOnClickListener { choose() }
            partyUpdates.add {
                val current=vm.invoiceDraft.value
                val id=if(route=="clientes") current?.clientId.orEmpty() else current?.typeId.orEmpty()
                val selected=InvoicePresentation.byId(catalogs(),route,id)
                input.input.setText(if(route=="clientes") InvoicePresentation.clientName(selected) else InvoicePresentation.text(selected,"Nombre"),false)
            }
            layouts[key]=input.root; controls.add(input.input); body.addView(input.root)
        }
        body.addView(ui.text("DATOS DEL CLIENTE",14f,true))
        party("ClienteId","clientes","Cliente *")
        party("TipoContratoId","tipos-contrato","Tipo de contrato *")
        val emissionDate=ui.text(""); val emissionTime=ui.text(""); val due=ui.text(""); val summaryTiming=ui.text("",13f,true)
        body.addView(ui.text("FECHA DE EMISIÓN",12f,true)); body.addView(emissionDate)
        body.addView(ui.text("HORA DE EMISIÓN",12f,true)); body.addView(emissionTime)
        body.addView(ui.text(if(draft.id==null) "Fecha actual del dispositivo · se actualiza al generar" else "Emisión original de la factura",12f,true))
        body.addView(ui.text("VENCIMIENTO",12f,true)); body.addView(due)
        body.addView(ui.text("Calculado según tipo de contrato; se confirma al guardar",12f,true))
        fun preview() {
            val current=vm.invoiceDraft.value ?: return
            val stamp=if(current.id==null) runCatching { vm.previewInvoiceEmission() }.getOrNull() else runCatching {
                InvoiceEmission(ZonedDateTime.of(LocalDate.parse(current.values.getValue("Fecha_Emision")),LocalTime.parse(current.values.getValue("Hora_Emision")),ZoneId.systemDefault()))
            }.getOrNull()
            emissionDate.text=stamp?.let { InvoicePresentation.date(it.date) } ?: "Verifica la fecha del dispositivo"
            emissionTime.text=stamp?.let { InvoicePresentation.time(it.time) }.orEmpty()
            val type=InvoicePresentation.byId(catalogs(),"tipos-contrato",current.typeId)
            due.text=if(stamp!=null && type!=null) InvoicePresentation.date(InvoiceTiming.dueDate(stamp.captured.toLocalDate(),InvoicePresentation.text(type,"Nombre")).toString())+" · "+InvoicePresentation.time(InvoiceTiming.dueTime.toString()) else "Selecciona un tipo de contrato."
            summaryTiming.text="Fecha de emisi\u00f3n: ${emissionDate.text} \u00b7 ${emissionTime.text}\nVencimiento: ${due.text}"
        }
        InvoicePresentation.editableHeaders(module()).filter { it.name=="CodigoFact" }.forEach { field ->
            val input=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),body,false)
            input.root.hint="Código de factura"+(if(field.required) " *" else "")
            input.input.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            input.input.setSingleLine(true); input.input.setText(draft.values[field.name].orEmpty())
            input.input.doOnTextChanged { text,_,_,_ ->
                vm.invoiceDraft.value=vm.invoiceDraft.value?.let { it.copy(values=it.values+(field.name to text.toString().trim())) }
                vm.invoiceErrors.value=vm.invoiceErrors.value-field.name
            }
            layouts[field.name]=input.root; controls.add(input.input); body.addView(input.root)
        }
        body.addView(ui.text("SERVICIOS",14f,true))
        val lineContainer=ui.column(0); body.addView(lineContainer)
        val lineUpdaters=mutableListOf<()->Unit>()
        val lineControls=mutableListOf<View>()
        val lineErrors=mutableMapOf<Long,TextView>()
        fun updateLine(key: Long,change: (InvoiceLine)->InvoiceLine) {
            vm.invoiceDraft.value=vm.invoiceDraft.value?.let { it.copy(lines=it.lines.map { line -> if(line.key==key) change(line) else line }) }
            vm.invoiceErrors.value=vm.invoiceErrors.value-"line-$key"
        }
        fun renderLines() {
            lineContainer.removeAllViews(); lineUpdaters.clear(); lineControls.clear(); lineErrors.clear()
            vm.invoiceDraft.value?.lines.orEmpty().forEach { original ->
                val content=ui.column(); val header=LinearLayout(ui.ctx)
                val name=ui.text(InvoicePresentation.text(InvoicePresentation.service(original,catalogs()),"Nombre_Servicio").ifBlank { "Servicio no disponible" },18f)
                header.addView(name,LinearLayout.LayoutParams(0,-2,1f))
                val remove=ui.button("",false) {
                    if(vm.invoiceSaving.value) return@button
                    vm.invoiceDraft.value=vm.invoiceDraft.value?.let { it.copy(lines=it.lines.filter { line -> line.key!=original.key }) }
                    vm.invoiceErrors.value=vm.invoiceErrors.value-"line-${original.key}"; renderLines(); stateChanged()
                }.apply { setIconResource(R.drawable.ic_invoice_remove); iconTint=ColorStateList.valueOf(ui.color(R.color.login_error)); iconPadding=0; contentDescription="Quitar ${name.text}"; setPadding(0,0,0,0) }
                header.addView(remove,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48))); content.addView(header)
                val stepper=LinearLayout(ui.ctx).apply { isBaselineAligned=false; gravity=Gravity.CENTER_VERTICAL }
                val quantity=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),stepper,false); quantity.root.hint="Cantidad"
                quantity.input.inputType=InputType.TYPE_CLASS_NUMBER; quantity.input.setText(original.quantity); quantity.input.setSingleLine(true)
                val minus=ui.button("−",false) { val old=quantity.input.text.toString().toIntOrNull() ?: 1; if(old>1) quantity.input.setText((old-1).toString()) }
                val plus=ui.button("+",false) { val old=quantity.input.text.toString().toIntOrNull() ?: 0; if(old<Int.MAX_VALUE) quantity.input.setText((old+1).toString()) }
                stepper.addView(minus,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
                stepper.addView(quantity.root,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=ui.dp(8); marginEnd=ui.dp(8) })
                stepper.addView(plus,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48))); content.addView(stepper)
                quantity.input.doOnTextChanged { text,_,_,_ -> updateLine(original.key) { it.copy(quantity=text.toString()) } }
                content.addView(ui.text("Precio unitario",13f,true))
                val price=ui.text("",20f); content.addView(price)
                content.addView(ui.text(if(original.preservedPrice) "Precio aplicado al facturar" else "Tarifa registrada del servicio",13f,true))
                val subtotal=ui.text("",19f).apply { setTextColor(ui.color(R.color.login_cyan)) }; content.addView(subtotal)
                val error=ui.text("",14f).apply { setTextColor(ui.color(R.color.login_error)) }; content.addView(error); lineErrors[original.key]=error
                lineUpdaters.add {
                    val current=vm.invoiceDraft.value?.lines?.find { it.key==original.key } ?: return@add
                    name.text=InvoicePresentation.text(InvoicePresentation.service(current,catalogs()),"Nombre_Servicio").ifBlank { "Servicio no disponible" }
                    remove.contentDescription="Quitar ${name.text}"
                    price.text=InvoicePresentation.money(InvoicePresentation.price(current,catalogs()))
                    subtotal.text="Subtotal: ${InvoicePresentation.money(InvoicePresentation.subtotal(current,catalogs()))}"
                    error.text=vm.invoiceErrors.value["line-${original.key}"].orEmpty()
                    error.visibility=if(error.text.isBlank()) View.GONE else View.VISIBLE
                }
                lineControls.addAll(listOf(remove,minus,plus,quantity.input)); lineContainer.addView(ui.card().apply { addView(content) })
            }
        }
        val add=ui.button("Agregar servicio",false) { chooseService { service ->
            if(vm.invoiceSaving.value) return@chooseService
            val current=vm.invoiceDraft.value ?: return@chooseService
            val key=(current.lines.maxOfOrNull { it.key } ?: 0)+1
            vm.invoiceDraft.value=current.copy(lines=current.lines+InvoiceLine(key,InvoicePresentation.text(service,"id")))
            vm.invoiceErrors.value=vm.invoiceErrors.value-"lineas"; renderLines(); stateChanged()
        } }.apply { setIconResource(R.drawable.ic_contract_add); iconSize=ui.dp(18); iconTint=ColorStateList.valueOf(ui.color(R.color.login_cyan)) }
        body.addView(add)
        body.addView(ui.text("RESUMEN",14f,true)); val summary=ui.text(""); val total=ui.text("",30f).apply { setTextColor(ui.color(R.color.login_cyan)) }
        body.addView(summary)
        body.addView(ui.text("TIPO DE FACTURA",13f,true)); val invoiceType=ui.text(""); body.addView(invoiceType)
        body.addView(ui.text("TOTAL ESTIMADO",13f,true)); body.addView(total)
        body.addView(summaryTiming)
        body.addView(ui.text("El total y el vencimiento definitivos se confirman al generar la factura.",13f,true))
        val error=ui.text("",14f).apply { setTextColor(ui.color(R.color.login_error)); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE }; body.addView(error)
        val progress=ProgressBar(ui.ctx).apply { visibility=View.GONE; contentDescription="Procesando factura" }; body.addView(progress,LinearLayout.LayoutParams(ui.dp(36),ui.dp(36)))
        val save=ui.button(if(draft.id==null) "Generar factura" else "Actualizar factura") { vm.saveInvoice(); stateChanged(); layouts.values.firstOrNull { it.error!=null }?.editText?.requestFocus() }
        val cancel=ui.button("Cancelar",false) { if(!vm.invoiceSaving.value) { vm.invoiceDraft.value=null; vm.invoiceErrors.value=emptyMap(); dismiss() } }
        body.addView(save); body.addView(cancel); renderLines()
        var shownErrors=emptyMap<String,String>()
        show(body) {
            val current=vm.invoiceDraft.value
            lineUpdaters.forEach { it() }
            summary.text="${current?.lines?.size ?: 0} servicios · ${current?.lines?.sumOf { InvoicePresentation.quantity(it)?.toLong() ?: 0 } ?: 0} unidades"
            invoiceType.text=InvoicePresentation.invoiceType(InvoicePresentation.byId(catalogs(),"tipos-contrato",current?.typeId.orEmpty()))
            summaryTiming.text="Fecha de emisión: ${emissionDate.text} · ${emissionTime.text}\nVencimiento: ${due.text}"
            total.text=current?.let { InvoicePresentation.money(InvoicePresentation.total(it,catalogs())) } ?: "No disponible"
            layouts.forEach { (key,layout) -> layout.error=vm.invoiceErrors.value[key] }
            val all=vm.invoiceErrors.value
            error.text=(listOfNotNull(all["detail"],all["non_field_errors"],all["lineas"])+all.filterKeys { it !in layouts && !it.startsWith("line-") && it !in setOf("detail","non_field_errors","lineas") }.values).joinToString("\n")
            error.visibility=if(error.text.isBlank()) View.GONE else View.VISIBLE
            val busy=vm.invoiceSaving.value
            (controls+lineControls+listOf(add,cancel)).forEach { it.isEnabled=!busy }
            save.isEnabled=!busy && (if(current?.id==null) module().permissions.create else module().permissions.update)
            save.text=if(busy) "Procesando…" else if(vm.invoiceRetryReady.value) "Reintentar generación" else if(draft.id==null) "Generar factura" else "Actualizar factura"
            progress.visibility=if(busy) View.VISIBLE else View.GONE
            active?.setCancelable(!busy); active?.setCanceledOnTouchOutside(!busy)
            if(!busy && all.isNotEmpty() && all!=shownErrors) {
                val target: View?=layouts.values.firstOrNull { !it.error.isNullOrBlank() }
                    ?: all.keys.firstNotNullOfOrNull { key -> key.removePrefix("line-").toLongOrNull()?.let { lineErrors[it] } }
                    ?: error.takeIf { it.visibility==View.VISIBLE }
                target?.post { target.requestRectangleOnScreen(android.graphics.Rect(0,0,target.width,target.height),true) }
            }
            if(!busy || all.isEmpty()) shownErrors=all
        }
        updateCatalogs={ partyUpdates.forEach { it() }; preview(); lineUpdaters.forEach { it() } }
        startClock { preview() }
        stateChanged()
    }
    private fun chooseParty(route: String,selected: (JsonObject)->Unit) {
        chooser?.dismiss()
        val customer=route=="clientes"
        val body=ui.column(); body.setBackgroundColor(ui.color(R.color.login_bg))
        body.addView(ui.text(if(customer) "Seleccionar cliente" else "Tipo de contrato",23f))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),body,false)
        search.root.hint=if(customer) "Buscar por nombre, c?dula o correo?" else "Buscar tipos de contrato?"; body.addView(search.root)
        val list=ui.column(0); body.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val normalized=com.softlutionic.mobile.ui.contracts.ContractPresentation.normalize(query)
            val values=catalogs()[route].orEmpty().filter { row ->
                (!customer || InvoicePresentation.activeClient(row)) && com.softlutionic.mobile.ui.contracts.ContractPresentation.normalize(
                    if(customer) InvoicePresentation.clientName(row)+" "+InvoicePresentation.text(row,"Cedula")+" "+InvoicePresentation.text(row,"Correo")
                    else InvoicePresentation.text(row,"Nombre")+" "+InvoicePresentation.text(row,"Descripcion")).contains(normalized)
            }
            if(values.isEmpty()) list.addView(ui.text(if(customer) "No hay clientes activos disponibles." else "No hay tipos de contrato disponibles.",14f,true))
            values.forEach { row ->
                val content=ui.column()
                content.addView(ui.text(if(customer) InvoicePresentation.clientName(row) else InvoicePresentation.text(row,"Nombre"),18f))
                val secondary=if(customer) listOf(InvoicePresentation.text(row,"Cedula"),InvoicePresentation.text(row,"Correo")).filter { it.isNotBlank() }.joinToString(" · ") else InvoicePresentation.text(row,"Descripcion")
                if(secondary.isNotBlank()) content.addView(ui.text(secondary,14f,true))
                list.addView(ui.card().apply { addView(content); isClickable=true; isFocusable=true; setOnClickListener { if(!vm.invoiceSaving.value) selected(row); chooser?.dismiss(); chooser=null } })
            }
        }
        search.input.doOnTextChanged { value,_,_,_ -> render(value.toString()) }; render("")
        body.addView(ui.button("Cerrar",false) { chooser?.dismiss(); chooser=null })
        chooser=BottomSheetDialog(ui.ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(context).apply { addView(body) }); show(); behavior.state=BottomSheetBehavior.STATE_EXPANDED
        }
    }
    private fun chooseService(selected: (JsonObject)->Unit) {
        if(vm.invoiceSaving.value) return
        chooser?.dismiss()
        val body=ui.column(); body.setBackgroundColor(ui.color(R.color.login_bg)); body.addView(ui.text("Agregar servicio",23f))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),body,false); search.root.hint="Buscar servicios…"; body.addView(search.root)
        val list=ui.column(0); body.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val values=catalogs()["servicios"].orEmpty().filter { com.softlutionic.mobile.ui.contracts.ContractPresentation.normalize(InvoicePresentation.text(it,"Nombre_Servicio")+" "+InvoicePresentation.text(it,"Descripcion")).contains(com.softlutionic.mobile.ui.contracts.ContractPresentation.normalize(query)) }
            if(values.isEmpty()) list.addView(ui.text("No hay servicios disponibles. Actualiza los catálogos.",14f,true))
            values.forEach { service ->
                val content=ui.column(); content.addView(ui.text(InvoicePresentation.text(service,"Nombre_Servicio"),18f))
                content.addView(ui.text(InvoicePresentation.text(service,"Descripcion"),14f,true).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
                content.addView(ui.text(InvoicePresentation.money(InvoicePresentation.decimal(service,"Precio")),19f))
                list.addView(ui.card().apply { addView(content); isClickable=true; isFocusable=true; setOnClickListener { selected(service); chooser?.dismiss(); chooser=null } })
            }
        }
        search.input.doOnTextChanged { value,_,_,_ -> render(value.toString()) }; render("")
        body.addView(ui.button("Cerrar",false) { chooser?.dismiss(); chooser=null })
        chooser=BottomSheetDialog(ui.ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(context).apply { addView(body) }); show(); behavior.state=BottomSheetBehavior.STATE_EXPANDED
        }
    }
}
