package com.softlutionic.mobile.ui.payments

import android.view.*
import android.widget.*
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.JsonObject
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ClientInputBinding
import com.softlutionic.mobile.ui.invoices.InvoiceViews
import com.softlutionic.mobile.ui.invoices.InvoicePresentation as I
import com.softlutionic.mobile.viewmodel.MainViewModel

class PaymentsController(private val host: AppCompatActivity,private val initial: Module,private val vm: MainViewModel) {
    private val ui=InvoiceViews(host)
    val root=ui.column()
    private val module get()=vm.effectiveModule(vm.modules.value.find { it.name=="pagos" } ?: initial)
    private val filters=ChipGroup(ui.ctx).apply { isSingleSelection=true; isSelectionRequired=true }
    private val notice=ui.text("",13f,true)
    private val adapter=PaymentsAdapter()
    private var sheet: BottomSheetDialog?=null
    private var save: com.google.android.material.button.MaterialButton?=null
    private var error: TextView?=null
    private var editable=emptyList<View>()
    private var detailData: JsonObject?=null
    private var detailBody: LinearLayout?=null
    private var filterSignature=""
    init {
        root.setBackgroundColor(ui.color(R.color.login_bg))
        root.addView(ui.text("PAGOS",26f))
        root.addView(ui.text("Gestiona facturas pendientes y pagos registrados.",14f,true))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),root,false)
        search.root.hint="Buscar por factura o cliente..."; search.input.setSingleLine(true)
        search.input.setText(vm.paymentFilters.value.query)
        search.input.doOnTextChanged { text,_,_,_ -> vm.paymentFilters.value=vm.paymentFilters.value.copy(query=text.toString()) }
        root.addView(search.root)
        root.addView(ui.text("Estado",13f,true))
        root.addView(HorizontalScrollView(ui.ctx).apply { isHorizontalScrollBarEnabled=false; addView(filters) })
        root.addView(notice)
        root.addView(RecyclerView(ui.ctx).apply { layoutManager=LinearLayoutManager(ui.ctx); adapter=this@PaymentsController.adapter },LinearLayout.LayoutParams(-1,0,1f))
        vm.loadPaymentCatalogs(); stateChanged()
    }
    private fun invoices()=vm.paymentCatalogs.value["facturas"].orEmpty().map { PaymentPresentation.invoice(it,vm.paymentCatalogs.value) }
    private fun payments()=vm.paymentCatalogs.value["pagos"].orEmpty().map { PaymentPresentation.payment(it,vm.paymentCatalogs.value) }
    fun submit(json: String?) { /* Shared Room content is observed by MainActivity; catalogs own both sections. */ stateChanged() }
    fun stateChanged() {
        val states=vm.paymentCatalogs.value["estados-factura"].orEmpty()
        val signature=states.toString()+vm.paymentFilters.value.stateId
        if(signature!=filterSignature) {
            filterSignature=signature; filters.removeAllViews()
            val options=listOf(null to "Todos")+states.map { I.text(it,"id") to I.text(it,"estado") }
            options.forEach { (id,name) -> filters.addView(Chip(ui.ctx).apply {
                text=name; isCheckable=true; isChecked=vm.paymentFilters.value.stateId==id
                setTextColor(ui.color(R.color.login_text)); chipBackgroundColor=android.content.res.ColorStateList.valueOf(ui.color(if(isChecked) R.color.contracts_primary else R.color.login_surface))
                setOnClickListener { vm.paymentFilters.value=vm.paymentFilters.value.copy(stateId=id); stateChanged() }
            }) }
        }
        notice.text=when { vm.paymentLoading.value -> "Actualizando facturas y pagos..."; else -> vm.paymentNotice.value }
        notice.visibility=if(notice.text.isBlank()) View.GONE else View.VISIBLE
        renderList()
        save?.isEnabled=!vm.paymentSaving.value
        save?.text=if(vm.paymentSaving.value) "Procesando..." else if(save?.tag=="edit") "Actualizar pago" else "Registrar pago"
        editable.forEach { it.isEnabled=!vm.paymentSaving.value }
        error?.text=vm.paymentError.value
        sheet?.setCancelable(!vm.paymentSaving.value)
        renderDetail()
        vm.paymentSaved.value?.let { saved ->
            if(root.isAttachedToWindow) {
                vm.paymentSaved.value=null; dismiss(); detail(PaymentPresentation.payment(saved,vm.paymentCatalogs.value))
                com.google.android.material.snackbar.Snackbar.make(root,vm.message.value,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
    }
    fun renderList() {
        val filter=vm.paymentFilters.value
        val pending=invoices().filter { it.payable && PaymentPresentation.matches(it,filter) }
        val history=payments().filter { PaymentPresentation.matches(it.invoice,filter) }
        adapter.items=buildList {
            add(Heading("FACTURAS PENDIENTES A PAGAR","Facturas que todavía requieren gestión de pago. · ${pending.size} resultados"))
            if(pending.isEmpty()) add(Empty(if(filter.query.isNotBlank()) "No hay facturas que coincidan con la búsqueda y el estado." else when(vm.paymentCatalogs.value["estados-factura"].orEmpty().find { I.text(it,"id")==filter.stateId }?.let { I.text(it,"estado").lowercase() }) {
                "vencida" -> "No hay facturas vencidas."; "pagada" -> "Las facturas pagadas se consultan en el historial."; else -> "No hay facturas pendientes."
            }))
            pending.forEach { add(Pending(it)) }
            add(Heading("PAGOS REGISTRADOS","Historial de pagos registrados. · ${history.size} resultados"))
            if(history.isEmpty()) add(Empty("No hay pagos que coincidan con el filtro."))
            history.forEach { add(Paid(it)) }
        }
        adapter.notifyDataSetChanged()
    }
    private fun open(title: String): LinearLayout {
        dismiss()
        val body=ui.column().apply { setBackgroundColor(ui.color(R.color.login_bg)); addView(ui.text(title,24f)) }
        sheet=BottomSheetDialog(ui.ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(ui.ctx).apply { isFillViewport=true; addView(body) })
            setOnDismissListener { save=null; error=null; editable=emptyList() }
            show(); behavior.skipCollapsed=true
            findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { it.layoutParams=it.layoutParams.apply { height=ViewGroup.LayoutParams.MATCH_PARENT } }
            behavior.state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return body
    }
    private fun summary(body: LinearLayout,invoice: PaymentInvoice?) {
        body.addView(ui.text("FACTURA",12f,true)); body.addView(ui.text(invoice?.code?.ifBlank { "Código no disponible" } ?: "Factura no disponible",22f))
        body.addView(ui.text("CLIENTE",12f,true)); body.addView(ui.text(invoice?.client ?: "Cliente no disponible",18f))
        body.addView(ui.badge(invoice?.state ?: "Estado no disponible"),LinearLayout.LayoutParams(-2,-2))
    }
    private fun detail(row: PaymentRow) {
        val body=open("DETALLE DEL PAGO")
        detailData=row.data; detailBody=body; renderDetail()
    }
    private fun renderDetail() {
        val body=detailBody ?: return
        val data=detailData ?: return
        val row=PaymentPresentation.payment(data,vm.paymentCatalogs.value)
        body.removeAllViews(); body.addView(ui.text("DETALLE DEL PAGO",24f))
        body.addView(ui.text(PaymentPresentation.receipt(row.data),22f)); summary(body,row.invoice)
        body.addView(ui.text(PaymentPresentation.dateTime(I.text(row.data,"Fecha_Pago")),14f,true))
        body.addView(ui.text(I.money(I.decimal(row.data,"Monto_Pagado")),26f))
        body.addView(ui.text(PaymentPresentation.method(row.data)))
        body.addView(ui.text(if(PaymentPresentation.confirmed(row.data)) "Confirmado" else "No confirmado"))
        if(module.permissions.update && row.invoice!=null) body.addView(ui.button("Editar",false) { form(row.invoice,row.data) })
        body.addView(ui.button("Cerrar",false) { dismiss() })
    }
    private fun form(invoice: PaymentInvoice,existing: JsonObject?=null) {
        if(vm.paymentSaving.value || !(if(existing==null) module.permissions.create else module.permissions.update)) return
        vm.paymentError.value=""
        val body=open(if(existing==null) "Registrar pago" else "Editar pago")
        summary(body,invoice)
        body.addView(ui.text(if(existing==null) "MONTO TOTAL" else "MONTO DE LA FACTURA",12f,true))
        body.addView(ui.text(I.money(I.decimal(invoice.data,"Monto_Total")),26f))
        if(existing==null) body.addView(ui.text("Se registra el monto total. La confirmación del pago actualiza el estado en el servidor.",13f,true))
        fun writable(key: String)=module.fields.any { it.name==key && !it.read_only }
        fun input(label: String,value: String,type: Int): EditText {
            val binding=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),body,false)
            binding.root.hint=label; binding.input.setSingleLine(true); binding.input.inputType=type; binding.input.setText(value); body.addView(binding.root)
            return binding.input
        }
        val receipt=input("Número de recibo",existing?.let { I.text(it,"NumeroRecibo") }.orEmpty(),InputType.TYPE_CLASS_NUMBER).apply { isEnabled=writable("NumeroRecibo") }
        val amount=if(existing!=null) input("Monto pagado",I.text(existing,"Monto_Pagado"),InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply { isEnabled=writable("Monto_Pagado") } else null
        val transfer=existing?.get("MetodoPago")?.asBoolean ?: false
        val method=com.google.android.material.textfield.MaterialAutoCompleteTextView(ui.ctx).apply {
            setAdapter(ArrayAdapter(ui.ctx,R.layout.client_dropdown_item,listOf("Efectivo","Transferencia")))
            setText(if(transfer) "Transferencia" else "Efectivo",false); inputType=InputType.TYPE_NULL
            isEnabled=writable("MetodoPago"); setTextColor(ui.color(R.color.login_text)); setOnClickListener { showDropDown() }; minHeight=ui.dp(52)
        }
        body.addView(ui.text("Método de pago",13f,true)); body.addView(method)
        val confirm=com.google.android.material.materialswitch.MaterialSwitch(ui.ctx).apply {
            textOn=""; textOff=""; showText=false
            text="Pago confirmado"; setTextColor(ui.color(R.color.login_text)); isChecked=existing?.let { PaymentPresentation.confirmed(it) } ?: true
            isEnabled=existing!=null && writable("EstadoPago")
        }
        body.addView(confirm)
        var paymentDate=existing?.let { I.text(it,"Fecha_Pago") }
        val dateLabel=ui.text(if(existing==null) "Fecha y hora del dispositivo al registrar; requiere ajustes automáticos." else "Fecha del pago: ${PaymentPresentation.dateTime(paymentDate.orEmpty())}",13f,true)
        body.addView(dateLabel)
        val dateButton=if(existing!=null && writable("Fecha_Pago")) ui.button("Cambiar fecha y hora",false) {
            val current=runCatching { java.time.OffsetDateTime.parse(paymentDate).atZoneSameInstant(java.time.ZoneId.systemDefault()) }.getOrDefault(java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()))
            android.app.DatePickerDialog(host,{ _,year,month,day ->
                android.app.TimePickerDialog(host,{ _,hour,minute ->
                    paymentDate=java.time.ZonedDateTime.of(year,month+1,day,hour,minute,0,0,java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()
                    dateLabel.text="Fecha del pago: ${PaymentPresentation.dateTime(paymentDate.orEmpty())}"
                },current.hour,current.minute,false).show()
            },current.year,current.monthValue-1,current.dayOfMonth).show()
        }.also { body.addView(it) } else null
        error=ui.text("",14f).apply { setTextColor(ui.color(R.color.login_error)) }; body.addView(error)
        editable=listOfNotNull(receipt.takeIf { it.isEnabled },amount?.takeIf { it.isEnabled },method.takeIf { it.isEnabled },confirm.takeIf { it.isEnabled },dateButton)
        save=ui.button(if(existing==null) "Registrar pago" else "Actualizar pago") {
            vm.savePayment(invoice.id,existing,receipt.text.toString(),method.text.toString()=="Transferencia",amount?.text?.toString(),confirm.isChecked,paymentDate)
            stateChanged()
        }.apply { if(existing!=null) tag="edit" }
        body.addView(save)
        body.addView(ui.button("Cancelar",false) { if(!vm.paymentSaving.value) dismiss() })
        stateChanged()
    }
    private fun dismiss() { sheet?.dismiss(); sheet=null; save=null; error=null; editable=emptyList(); detailData=null; detailBody=null }
    fun dispose()=dismiss()
    private sealed interface Item
    private data class Heading(val title: String,val subtitle: String): Item
    private data class Empty(val text: String): Item
    private data class Pending(val invoice: PaymentInvoice): Item
    private data class Paid(val payment: PaymentRow): Item
    private inner class PaymentsAdapter: RecyclerView.Adapter<PaymentsAdapter.Holder>() {
        var items=emptyList<Item>()
        inner class Holder(val body: LinearLayout): RecyclerView.ViewHolder(body)
        override fun getItemCount()=items.size
        override fun onCreateViewHolder(parent: ViewGroup,type: Int)=Holder(ui.column(0).apply { layoutParams=RecyclerView.LayoutParams(-1,-2) })
        override fun onBindViewHolder(holder: Holder,position: Int) {
            holder.body.removeAllViews()
            when(val item=items[position]) {
                is Heading -> { holder.body.addView(ui.text(item.title,18f)); holder.body.addView(ui.text(item.subtitle,13f,true)) }
                is Empty -> holder.body.addView(ui.text(item.text,14f,true))
                else -> {
                    val card=ui.card(); val body=ui.column(); card.addView(body); holder.body.addView(card)
                    when(item) {
                        is Pending -> {
                            val row=item.invoice; summary(body,row)
                            body.addView(ui.text("Vence · ${I.date(I.text(row.data,"Fecha_Vencimiento"))} · ${I.time(I.text(row.data,"Hora_Vencimiento"))}",14f,true))
                            body.addView(ui.text(I.money(I.decimal(row.data,"Monto_Total")),26f))
                            val existing=payments().firstOrNull { I.text(it.data,"FacturaId")==row.id }
                            if(existing==null && module.permissions.create) body.addView(ui.button("Registrar pago") { form(row) }.apply { isEnabled=!vm.paymentSaving.value })
                            else if(existing!=null) body.addView(ui.button("Consultar pago",false) { detail(existing) })
                        }
                        is Paid -> {
                            val row=item.payment; body.addView(ui.text(PaymentPresentation.receipt(row.data),22f)); summary(body,row.invoice)
                            body.addView(ui.text(PaymentPresentation.dateTime(I.text(row.data,"Fecha_Pago")),14f,true))
                            body.addView(ui.text(PaymentPresentation.method(row.data),14f,true))
                            body.addView(ui.text(if(PaymentPresentation.confirmed(row.data)) "Confirmado" else "No confirmado",14f,true))
                            body.addView(ui.text(I.money(I.decimal(row.data,"Monto_Pagado")),26f))
                            body.addView(ui.button("Ver detalle",false) { detail(row) }); card.isClickable=true; card.isFocusable=true; card.setOnClickListener { detail(row) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
