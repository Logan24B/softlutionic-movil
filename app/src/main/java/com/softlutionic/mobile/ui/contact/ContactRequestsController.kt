package com.softlutionic.mobile.ui.contact

import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.JsonParser
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.ContactRequestDto
import com.softlutionic.mobile.databinding.ViewContactRequestsBinding
import com.softlutionic.mobile.domain.auth.AuthorizationPolicy
import com.softlutionic.mobile.ui.contracts.ContractPresentation
import com.softlutionic.mobile.ui.invoices.InvoiceViews
import com.softlutionic.mobile.ui.payments.PaymentPresentation
import com.softlutionic.mobile.viewmodel.MainViewModel

class ContactRequestsController(private val host: AppCompatActivity,private val view: ViewContactRequestsBinding,private val vm: MainViewModel) {
    private val ui=InvoiceViews(host)
    private var records=emptyList<ContactRequestDto>()
    private var parseError=false
    private var sheet: BottomSheetDialog?=null
    private val adapter=Cards()
    private val allowed get()=vm.authenticated.value && AuthorizationPolicy.canReadUsers(vm.currentIdentity.value) &&
        vm.modules.value.find { it.name==ContactRequestDto.MODULE }?.let { vm.effectiveModule(it).permissions.read }==true
    init {
        view.search.root.hint=host.getString(R.string.contact_search)
        view.search.input.setSingleLine(true)
        view.search.input.setText(vm.contactQuery.value)
        view.search.input.doOnTextChanged { text,_,_,_ -> vm.contactQuery.value=text.toString() }
        view.requests.layoutManager=LinearLayoutManager(host); view.requests.adapter=adapter
        view.retry.setOnClickListener { vm.refresh() }
        stateChanged()
    }
    fun submit(json: String?) {
        if(json==null) { records=emptyList(); parseError=false }
        else runCatching { JsonParser.parseString(json).asJsonArray.map { ContactRequestDto.fromJson(it.asJsonObject) } }
            .onSuccess { records=it; parseError=false }.onFailure { parseError=true }
        stateChanged()
    }
    fun stateChanged() {
        val state=vm.contactUiState.value
        val denied=!allowed || state.denied
        if(denied) dispose()
        val query=ContractPresentation.normalize(vm.contactQuery.value.trim())
        adapter.rows=if(denied || parseError) emptyList() else records.filter {
            ContractPresentation.normalize(listOfNotNull(it.nombre,it.correo,it.asunto,it.mensaje).joinToString(" ")).contains(query)
        }
        adapter.notifyDataSetChanged()
        view.summary.text=if(denied || parseError || records.isEmpty()) "" else "${adapter.rows.size} de ${records.size} solicitudes"
        view.status.text=when {
            denied -> "No tienes permiso para consultar solicitudes de contacto."
            parseError -> "No se pudo interpretar la respuesta de solicitudes."
            state.phase==ContactPhase.LOADING -> host.getString(R.string.contact_loading)
            state.phase==ContactPhase.ERROR -> state.error
            records.isEmpty() -> host.getString(R.string.contact_empty)
            adapter.rows.isEmpty() -> host.getString(R.string.contact_no_matches)
            else -> ""
        }
        view.status.visibility=if(view.status.text.isBlank()) View.GONE else View.VISIBLE
        view.retry.visibility=if(!denied && (parseError || state.phase==ContactPhase.ERROR)) View.VISIBLE else View.GONE
        view.retry.isEnabled=!vm.busy.value
    }
    private fun detail(row: ContactRequestDto) {
        if(!allowed) return
        dispose()
        val body=ui.column().apply { addView(ui.text(host.getString(R.string.contact_detail),24f)) }
        fun field(label: String,value: String?) {
            if(!value.isNullOrBlank()) { body.addView(ui.text(label,12f,true)); body.addView(ui.text(value)) }
        }
        field("Nombre",row.nombre); field("Correo",row.correo); field("Teléfono",row.telefono)
        field("Asunto",row.asunto); field("Mensaje",row.mensaje)
        row.fechaCreacion?.let { field("Fecha",PaymentPresentation.dateTime(it)) }
        field("Estado",row.estado); field("Prioridad",row.prioridad); field("Origen",row.origen); field("Tipo",row.tipo)
        body.addView(ui.button("Cerrar",false) { dispose() })
        sheet=BottomSheetDialog(host,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(host).apply { addView(body) }); show()
            behavior.state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }
    fun dispose() { sheet?.dismiss(); sheet=null }
    private inner class Cards: RecyclerView.Adapter<Cards.Holder>() {
        var rows=emptyList<ContactRequestDto>()
        inner class Holder(val card: com.google.android.material.card.MaterialCardView,val body: LinearLayout): RecyclerView.ViewHolder(card)
        override fun getItemCount()=rows.size
        override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
            val body=ui.column()
            val card=ui.card().apply { layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=ui.dp(12) }; addView(body); isClickable=true; isFocusable=true }
            return Holder(card,body)
        }
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val row=rows[position]; holder.body.removeAllViews()
            holder.body.addView(ui.text(row.nombre?.takeIf(String::isNotBlank) ?: "Solicitud de contacto",20f))
            row.asunto?.takeIf(String::isNotBlank)?.let { holder.body.addView(ui.text(it,16f)) }
            row.fechaCreacion?.let { holder.body.addView(ui.text(PaymentPresentation.dateTime(it),14f,true)) }
            row.estado?.takeIf(String::isNotBlank)?.let { holder.body.addView(ui.badge(it)) }
            holder.body.addView(ui.text("Ver detalle",14f,true))
            holder.card.setOnClickListener { detail(row) }
        }
    }
}
