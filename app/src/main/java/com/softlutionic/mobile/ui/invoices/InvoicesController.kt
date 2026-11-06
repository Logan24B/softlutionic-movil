package com.softlutionic.mobile.ui.invoices

import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.*
import com.google.android.material.snackbar.Snackbar
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ClientInputBinding
import com.softlutionic.mobile.viewmodel.MainViewModel

class InvoicesController(host: AppCompatActivity,private val initial: Module,private val vm: MainViewModel) {
    private val ui=InvoiceViews(host)
    val root=ui.column()
    private val module get()=vm.effectiveModule(vm.modules.value.find { it.name=="facturas" } ?: initial)
    private val sheets=InvoiceSheets(host,vm,{ module })
    private var records=emptyList<JsonObject>()
    private val empty=ui.text("")
    private val notice=ui.text("",13f,true)
    private val count=ui.text("",13f,true)
    private val create=ui.button("Nueva factura") { vm.beginInvoice(); sheets.form() }
    private val refresh=ui.button("Actualizar",false) { if(!vm.busy.value && !vm.invoiceSaving.value) { vm.refresh(); vm.loadInvoiceCatalogs(true) } }
    private val adapter=InvoiceAdapter()
    init {
        root.setBackgroundColor(ui.color(R.color.login_bg))
        root.addView(ui.text("FACTURAS",26f)); root.addView(ui.text("Gestiona los servicios facturados a tus clientes.",14f,true))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),root,false)
        search.root.hint="Buscar facturas…"; search.input.setSingleLine(true); search.input.setText(vm.invoiceFilters.value.query)
        search.input.doOnTextChanged { value,_,_,_ -> vm.invoiceFilters.value=vm.invoiceFilters.value.copy(query=value.toString()) }
        root.addView(search.root)
        val actions=LinearLayout(ui.ctx).apply { isBaselineAligned=false; setPadding(0,ui.dp(12),0,ui.dp(8)) }
        create.setIconResource(R.drawable.ic_invoice); create.iconSize=ui.dp(18); create.iconTint=ColorStateList.valueOf(ui.color(R.color.login_text))
        refresh.setIconResource(R.drawable.ic_refresh); refresh.iconSize=ui.dp(16); refresh.iconTint=ColorStateList.valueOf(ui.color(R.color.login_cyan))
        listOf(create,refresh).forEach { it.maxLines=1; it.iconPadding=ui.dp(4); it.setPadding(ui.dp(6),0,ui.dp(6),0); androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(it,10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP) }
        actions.addView(create,LinearLayout.LayoutParams(0,ui.dp(52),.63f).apply { marginEnd=ui.dp(10) })
        actions.addView(refresh,LinearLayout.LayoutParams(0,ui.dp(52),.37f)); root.addView(actions)
        root.addView(ui.button("Filtrar facturas",false) { sheets.filters(rows()) })
        root.addView(count); root.addView(notice); root.addView(empty)
        root.addView(RecyclerView(ui.ctx).apply { layoutManager=LinearLayoutManager(ui.ctx); adapter=this@InvoicesController.adapter },LinearLayout.LayoutParams(-1,0,1f))
        vm.loadInvoiceCatalogs(); stateChanged()
        root.post { if(vm.invoiceDraft.value!=null) sheets.form(); stateChanged() }
    }
    private fun catalogs(): Map<String,List<JsonObject>> {
        val map=vm.invoiceCatalogs.value.toMutableMap()
        val embedded=records.mapNotNull { InvoicePresentation.nested(it,"contrato_detalle") }
        map["contratos"]=(map["contratos"].orEmpty()+embedded).distinctBy { InvoicePresentation.text(it,"id") }
        return map
    }
    private fun rows()=records.map { InvoicePresentation.row(it,catalogs()) }
    fun submit(json: String?) {
        if(json!=null) runCatching { JsonParser.parseString(json).asJsonArray.map { it.asJsonObject } }.onSuccess { records=it }
        val embedded=mapOf(
            "clientes" to (records.mapNotNull { InvoicePresentation.nested(InvoicePresentation.nested(it,"contrato_detalle"),"cliente_detalle") }+records.mapNotNull { InvoicePresentation.nested(it,"cliente_detalle") }),
            "tipos-contrato" to records.mapNotNull { InvoicePresentation.nested(InvoicePresentation.nested(it,"contrato_detalle"),"tipo_contrato_detalle") },
            "contratos" to records.mapNotNull { InvoicePresentation.nested(it,"contrato_detalle") },
            "servicios" to records.flatMap { InvoicePresentation.array(it,"detalles") }.mapNotNull { InvoicePresentation.nested(it,"servicio_detalle") },
            "estados-factura" to records.mapNotNull { InvoicePresentation.nested(it,"estado_detalle") })
        embedded.forEach { (route,values) -> if(values.isNotEmpty()) vm.invoiceCatalogs.value=vm.invoiceCatalogs.value+(route to
            (vm.invoiceCatalogs.value[route].orEmpty()+values).distinctBy { InvoicePresentation.text(it,"id") }) }
        renderList()
    }
    fun renderList() {
        adapter.rows=InvoicePresentation.visible(rows(),vm.invoiceFilters.value); adapter.notifyDataSetChanged()
        count.text="${adapter.rows.size} de ${records.size} facturas"
        empty.visibility=if(adapter.rows.isEmpty()) View.VISIBLE else View.GONE
        empty.text=when { vm.busy.value && records.isEmpty() -> "Cargando facturas…"; records.isEmpty() -> "No hay facturas registradas."; else -> "No hay coincidencias. Revisa la búsqueda y los filtros." }
        notice.text=when { vm.invoiceCatalogsLoading.value -> "Actualizando catálogos…"; vm.invoiceCatalogsUnavailable.value -> "Algunos catálogos no están disponibles. Se conservan los datos guardados."; else -> "" }
        notice.visibility=if(notice.text.isBlank()) View.GONE else View.VISIBLE
    }
    fun stateChanged() {
        create.visibility=if(module.permissions.create) View.VISIBLE else View.GONE
        create.isEnabled=!vm.busy.value && !vm.invoiceSaving.value; refresh.isEnabled=!vm.busy.value && !vm.invoiceSaving.value
        sheets.stateChanged(); renderList()
        vm.invoiceSaved.value?.let { data -> if(root.isAttachedToWindow) {
            vm.invoiceSaved.value=null; sheets.detail(data)
            Snackbar.make(root,vm.invoiceSaveMessage.value,Snackbar.LENGTH_LONG).show()
        } }
    }
    fun dispose()=sheets.dismiss()
    private inner class InvoiceAdapter: RecyclerView.Adapter<InvoiceAdapter.Holder>() {
        var rows=emptyList<InvoiceRow>()
        inner class Holder(val card: com.google.android.material.card.MaterialCardView,val body: LinearLayout): RecyclerView.ViewHolder(card)
        override fun getItemCount()=rows.size
        override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
            val card=ui.card().apply { layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=ui.dp(12) }; isClickable=true; isFocusable=true }
            val body=ui.column(); card.addView(body); return Holder(card,body)
        }
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val row=rows[position]; holder.body.removeAllViews()
            holder.body.addView(ui.text(if(row.code.isBlank()) "Factura" else "Factura ${row.code}",22f))
            holder.body.addView(ui.badge(row.state),LinearLayout.LayoutParams(-2,-2))
            holder.body.addView(ui.text(row.client,18f).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(ui.text(row.contract,14f,true).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(ui.text(InvoicePresentation.money(InvoicePresentation.decimal(row.data,"Monto_Total")),25f).apply { setTextColor(ui.color(R.color.login_cyan)) })
            holder.body.addView(ui.text(InvoicePresentation.date(InvoicePresentation.text(row.data,"Fecha_Emision")),14f,true))
            holder.body.addView(ui.text("Ver detalle  ›",14f).apply { gravity=Gravity.END; setTextColor(ui.color(R.color.login_accent)) })
            holder.card.setOnClickListener { sheets.detail(row.data) }
        }
    }
}
