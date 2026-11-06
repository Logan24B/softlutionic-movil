package com.softlutionic.mobile.ui.consultation

import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.gson.*
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ClientInputBinding
import com.softlutionic.mobile.ui.invoices.InvoiceViews
import com.softlutionic.mobile.ui.invoices.InvoicePresentation as I
import com.softlutionic.mobile.ui.payments.PaymentPresentation as P
import com.softlutionic.mobile.viewmodel.MainViewModel

/** Shared screen shell; each module builds its own domain cards and detail, never a CRUD form. */
class ConsultationController(private val host: AppCompatActivity,private val initial: Module,private val vm: MainViewModel) {
    private val ui=InvoiceViews(host)
    val root=ui.column()
    private val route=initial.name
    private val module get()=vm.effectiveModule(vm.modules.value.find { it.name==route } ?: initial)
    private val allowed get()=ConsultationPresentation.canRead(vm.currentIdentity.value,module)
    private val filter get()=vm.consultationFilters.value[route] ?: ConsultationFilter()
    private var records=emptyList<JsonObject>()
    private var parseError=false
    private var sheet: BottomSheetDialog?=null
    private var chipSignature=""
    private val chips=ChipGroup(ui.ctx).apply { isSingleSelection=true; isSelectionRequired=true }
    private val summary=ui.text("",16f)
    private val notice=ui.text("",13f,true)
    private val emptyTitle=ui.text("",22f)
    private val emptyDescription=ui.text("",14f,true)
    private val retry=ui.button("Reintentar",false) { vm.refresh() }
    private val adapter=Cards()
    private var detailRecord: JsonObject?=null
    private var detailBody: LinearLayout?=null
    init {
        require(route in ConsultationPresentation.routes)
        root.setBackgroundColor(ui.color(R.color.login_bg))
        root.addView(ui.text(when(route) { "moras" -> "MORAS"; "notificaciones" -> "NOTIFICACIONES"; else -> "AUDITORÍA" },26f))
        root.addView(ui.text(when(route) { "moras" -> "Consulta obligaciones vencidas y saldos en mora."; "notificaciones" -> "Consulta tus avisos del sistema."; else -> "Consulta la trazabilidad registrada por el sistema." },14f,true))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ui.ctx),root,false)
        search.root.hint=when(route) { "moras" -> "Buscar factura o cliente..."; "notificaciones" -> "Buscar título o mensaje..."; else -> "Buscar en auditoría..." }
        search.input.setSingleLine(true); search.input.setText(filter.query)
        search.input.doOnTextChanged { text,_,_,_ -> setFilter(filter.copy(query=text.toString())) }
        root.addView(search.root)
        if(route=="notificaciones") root.addView(ui.button("Activar avisos del teléfono",false) { (host as com.softlutionic.mobile.MainActivity).enableDeviceNotices() })
        if(route=="notificaciones") root.addView(ui.button("Filtrar por fecha",false) { noticeDateSheet() })
        if(route=="auditoria") root.addView(ui.button("Filtrar actividad",false) { filterSheet() })
        else root.addView(HorizontalScrollView(ui.ctx).apply { isHorizontalScrollBarEnabled=false; addView(chips) })
        root.addView(summary); root.addView(notice); root.addView(emptyTitle); root.addView(emptyDescription)
        root.addView(retry.apply { visibility=View.GONE })
        root.addView(RecyclerView(ui.ctx).apply { layoutManager=LinearLayoutManager(ui.ctx); adapter=this@ConsultationController.adapter },LinearLayout.LayoutParams(-1,0,1f))
        vm.loadConsultationCatalogs(route); stateChanged()
    }
    private fun setFilter(value: ConsultationFilter) { vm.consultationFilters.value=vm.consultationFilters.value+(route to value); stateChanged() }
    private fun moras()=records.map { ConsultationPresentation.mora(it,vm.consultationCatalogs.value) }
    private fun notices()=records.map { ConsultationPresentation.notice(it,vm.consultationCatalogs.value) }
    private fun audits()=records.map { ConsultationPresentation.audit(it,vm.consultationCatalogs.value) }
    fun submit(json: String?) {
        if(json!=null) runCatching { JsonParser.parseString(json).asJsonArray.map { it.asJsonObject } }.onSuccess { records=it; parseError=false }.onFailure { parseError=true }
        stateChanged()
    }
    fun stateChanged() {
        if(!allowed) { adapter.rows=emptyList(); adapter.notifyDataSetChanged(); summary.text=""; notice.text="No tienes permiso para consultar este módulo."; notice.visibility=View.VISIBLE; emptyTitle.visibility=View.GONE; emptyDescription.visibility=View.GONE; dispose(); return }
        val states=when(route) { "moras" -> moras().map { it.state }.distinct(); "notificaciones" -> listOf("Leída","No leída")+if(notices().any { it.read==null }) listOf("Estado no disponible") else emptyList(); else -> emptyList() }
        val signature=states.toString()+filter.state
        if(signature!=chipSignature) {
            chipSignature=signature; chips.removeAllViews()
            (listOf(null to "Todos")+states.map { it to it }).forEach { (value,label) -> chips.addView(Chip(ui.ctx).apply {
                text=label; isCheckable=true; isChecked=value==filter.state
                setTextColor(ui.color(R.color.login_text)); chipBackgroundColor=android.content.res.ColorStateList.valueOf(ui.color(if(isChecked) R.color.contracts_primary else R.color.login_surface))
                setOnClickListener { setFilter(filter.copy(state=value)) }
            }) }
        }
        adapter.rows=when(route) {
            "moras" -> ConsultationPresentation.visibleMoras(moras(),filter).map { it.data }
            "notificaciones" -> ConsultationPresentation.visibleNotices(notices(),filter).map { it.data }
            else -> ConsultationPresentation.visibleAudit(audits(),filter).map { it.data }
        }
        adapter.notifyDataSetChanged()
        summary.text=if(route=="moras") {
            val result=ConsultationPresentation.pendingSummary(adapter.rows.map { ConsultationPresentation.mora(it,vm.consultationCatalogs.value) })
            "Facturas con mora pendiente: ${result.first}\nMonto pendiente de mora: ${I.money(result.second)}\nResumen de los registros cargados que coinciden con el filtro."
        } else "${adapter.rows.size} de ${records.size} "+if(route=="notificaciones") "avisos" else "eventos"
        if(route=="notificaciones") summary.append(vm.unreadNotices.value?.let { "\nNo leídas: $it" } ?: "\nNo leídas: no disponible")
        if(route=="notificaciones" && (filter.start!=null || filter.end!=null)) summary.append("\nDesde: ${filter.start?.let(I::date) ?: "Sin límite"} · Hasta: ${filter.end?.let(I::date) ?: "Sin límite"}")
        val loadError=if(route=="notificaciones") vm.noticeLoadError.value else ""
        retry.visibility=if(route=="notificaciones" && (parseError || loadError.isNotBlank())) View.VISIBLE else View.GONE
        retry.isEnabled=!vm.busy.value
        notice.text=if(parseError) "No se pudo interpretar la información recibida." else if(loadError.isNotBlank()) loadError else vm.consultationNotice.value
        notice.visibility=if(notice.text.isBlank()) View.GONE else View.VISIBLE
        val empty=adapter.rows.isEmpty()
        emptyTitle.visibility=if(empty) View.VISIBLE else View.GONE; emptyDescription.visibility=emptyTitle.visibility
        emptyTitle.text=when {
            vm.busy.value && records.isEmpty() -> "Cargando información..."
            route=="notificaciones" && (parseError || loadError.isNotBlank()) -> "No se pudieron cargar las notificaciones"
            records.isNotEmpty() -> "Sin coincidencias"
            route=="moras" -> "No hay facturas en mora"
            route=="notificaciones" -> "Sin notificaciones"
            else -> "Sin actividad registrada"
        }
        emptyDescription.text=when {
            route=="notificaciones" && vm.busy.value && records.isEmpty() -> "Espera mientras consultamos tus notificaciones."
            route=="notificaciones" && (parseError || loadError.isNotBlank()) -> "Utiliza Actualizar para volver a intentar."
            records.isNotEmpty() -> "Revisa la búsqueda y los filtros."
            route=="moras" -> "Actualmente no existen obligaciones vencidas."
            route=="notificaciones" -> "No tienes notificaciones por el momento."
            else -> "Los eventos del sistema aparecerán aquí."
        }
        renderDetail()
    }
    private fun open(title: String): LinearLayout {
        dispose()
        val body=ui.column().apply { setBackgroundColor(ui.color(R.color.login_bg)); addView(ui.text(title,24f)) }
        sheet=BottomSheetDialog(ui.ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(ui.ctx).apply { isFillViewport=true; addView(body) })
            show(); behavior.skipCollapsed=true
            findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { it.layoutParams=it.layoutParams.apply { height=-1 } }
            behavior.state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        return body
    }
    private fun detail(data: JsonObject) {
        if(!allowed) return
        val title=when(route) { "moras" -> "DETALLE DE MORA"; "notificaciones" -> "DETALLE DEL AVISO"; else -> "DETALLE DE AUDITORÍA" }
        detailBody=open(title); detailRecord=data; renderDetail()
        if(route=="notificaciones" && ConsultationPresentation.bool(data,"leida")==false && ConsultationPresentation.canMarkRead(module)) {
            I.text(data,"id").takeIf { it.isNotBlank() }?.let(vm::markNoticeRead)
        }
    }
    private fun field(body: LinearLayout,label: String,value: String) { body.addView(ui.text(label,12f,true)); body.addView(ui.text(value.ifBlank { "No registrado" })) }
    private fun renderDetail() {
        val body=detailBody ?: return; val original=detailRecord ?: return
        val data=records.find { if(route=="auditoria") it["IdLog"]==original["IdLog"] else it["id"]==original["id"] } ?: original
        body.removeAllViews()
        when(route) {
            "moras" -> {
                body.addView(ui.text("DETALLE DE MORA",24f)); val row=ConsultationPresentation.mora(data,vm.consultationCatalogs.value)
                moraBody(body,row)
                field(body,"Inicio",I.date(I.text(data,"Fecha_Inicio"))+" · "+I.time(I.text(data,"Hora_Inicio")))
                field(body,"Final del período registrado",I.date(I.text(data,"Fecha_Final"))+" · "+I.time(I.text(data,"Hora_Final")))
                field(body,"Descripción",I.text(data,"DescripcionMora"))
            }
            "notificaciones" -> {
                body.addView(ui.text("DETALLE DEL AVISO",24f)); noticeBody(body,ConsultationPresentation.notice(data,vm.consultationCatalogs.value),true)
            }
            else -> {
                body.addView(ui.text("DETALLE DE AUDITORÍA",24f)); val row=ConsultationPresentation.audit(data,vm.consultationCatalogs.value)
                field(body,"Usuario registrado",row.user); field(body,"Acción",row.action); field(body,"Entidad",row.entity)
                if(row.target.isNotBlank()) field(body,"Registro",row.target)
                field(body,"Fecha y hora",P.dateTime(I.text(data,"Fecha")))
                if(!row.technicalDescription) field(body,"Descripción",row.description)
                val technical=ui.column(0).apply { visibility=View.GONE }
                field(technical,"Identificador de evento",I.text(data,"IdLog")); field(technical,"Clave principal registrada",I.text(data,"ClavePrincipal")); field(technical,"Host",I.text(data,"Host"))
                if(row.technicalDescription) field(technical,"Detalle registrado",row.description)
                body.addView(ui.button("Información técnica",false) { technical.visibility=if(technical.visibility==View.VISIBLE) View.GONE else View.VISIBLE })
                body.addView(technical)
            }
        }
        body.addView(ui.button("Cerrar",false) { dispose() })
    }
    private fun moraBody(body: LinearLayout,row: MoraCard) {
        body.addView(ui.text(row.invoice,22f)); body.addView(ui.text(row.client,18f))
        body.addView(ui.badge(row.invoiceState),LinearLayout.LayoutParams(-2,-2))
        field(body,"Vencimiento de factura",row.due)
        field(body,"Estado de la mora",row.state)
        body.addView(ui.text("Monto de mora",12f,true)); body.addView(ui.text(I.money(I.decimal(row.data,"Monto_Mora")),26f).apply { setTextColor(ui.color(R.color.login_cyan)) })
    }
    private fun noticeBody(body: LinearLayout,row: NoticeCard,detail: Boolean=false) {
        body.addView(ui.text(row.title.ifBlank { "Aviso del sistema" },18f)); body.addView(ui.text(row.message.ifBlank { "Mensaje no disponible" },20f))
        body.addView(ui.text(P.dateTime(row.date),14f,true))
        if(detail) {
            I.text(row.data,"tipo").takeIf { it.isNotBlank() }?.let { field(body,"Tipo",it) }
            I.text(row.data,"prioridad").takeIf { it.isNotBlank() }?.let { field(body,"Prioridad",it) }
        }
        val state=when(row.read) { true -> "Leída"; false -> "No leída"; null -> null }
        if(state!=null) body.addView(ui.badge(state),LinearLayout.LayoutParams(-2,-2))
        if(detail && row.read==false && ConsultationPresentation.canMarkRead(module)) body.addView(ui.button(if(vm.noticeSaving.value==null) "Marcar como leída" else "Actualizando...",false) { vm.markNoticeRead(I.text(row.data,"id")) }.apply { isEnabled=vm.noticeSaving.value==null })
    }
    private fun noticeDateSheet() {
        if(!allowed) return
        val body=open("FILTRAR POR FECHA")
        var draft=filter
        fun dateButton(start: Boolean) {
            val label=if(start) "Desde" else "Hasta"
            fun value()=(if(start) draft.start else draft.end)?.let(I::date) ?: "Sin límite"
            lateinit var button: com.google.android.material.button.MaterialButton
            button=ui.button("$label: ${value()}",false) {
                val selected=if(start) draft.start else draft.end
                val date=runCatching { java.time.LocalDate.parse(selected) }.getOrDefault(java.time.LocalDate.now())
                android.app.DatePickerDialog(host,{ _,year,month,day ->
                    val chosen=java.time.LocalDate.of(year,month+1,day).toString()
                    draft=if(start) draft.copy(start=chosen) else draft.copy(end=chosen)
                    button.text="$label: ${value()}"
                },date.year,date.monthValue-1,date.dayOfMonth).show()
            }
            body.addView(button)
        }
        dateButton(true); dateButton(false)
        body.addView(ui.text("Se usa la fecha de creación en la zona horaria del dispositivo. Ambos días están incluidos.",14f,true))
        val error=ui.text("",14f); body.addView(error)
        body.addView(ui.button("Aplicar filtros") {
            if(draft.start!=null && draft.end!=null && draft.start!!>draft.end!!) error.text="La fecha inicial no puede ser posterior a la final."
            else { setFilter(draft); dispose() }
        })
        body.addView(ui.button("Limpiar fechas",false) { setFilter(filter.copy(start=null,end=null)); dispose() })
        body.addView(ui.button("Cancelar",false) { dispose() })
    }
    private fun filterSheet() {
        if(!allowed) return
        val body=open("Filtrar auditoría"); var draft=filter
        fun selector(label: String,values: List<String>,selected: String?,onPick: (String?)->Unit) {
            body.addView(ui.text(label,13f,true))
            body.addView(MaterialAutoCompleteTextView(ui.ctx).apply {
                val options=listOf("Todos")+values.distinct().sorted()
                setAdapter(ArrayAdapter(ui.ctx,R.layout.client_dropdown_item,options)); setText(selected ?: "Todos",false)
                inputType=android.text.InputType.TYPE_NULL; minHeight=ui.dp(52); setTextColor(ui.color(R.color.login_text)); setOnClickListener { showDropDown() }
                setOnItemClickListener { _,_,position,_ -> onPick(if(position==0) null else options[position]) }
            })
        }
        val rows=audits()
        selector("Usuario",rows.map { it.user },draft.user) { draft=draft.copy(user=it) }
        selector("Acción",rows.map { it.action },draft.action) { draft=draft.copy(action=it) }
        selector("Entidad",rows.map { it.entity },draft.entity) { draft=draft.copy(entity=it) }
        fun dateButton(start: Boolean) {
            val label=if(start) "Desde" else "Hasta"
            lateinit var button: com.google.android.material.button.MaterialButton
            button=ui.button("$label: ${if(start) draft.start ?: "Sin límite" else draft.end ?: "Sin límite"}",false) {
                val date=java.time.LocalDate.now()
                android.app.DatePickerDialog(host,{ _,year,month,day ->
                    val value=java.time.LocalDate.of(year,month+1,day).toString()
                    draft=if(start) draft.copy(start=value) else draft.copy(end=value); button.text="$label: ${I.date(value)}"
                },date.year,date.monthValue-1,date.dayOfMonth).show()
            }; body.addView(button)
        }
        dateButton(true); dateButton(false)
        val error=ui.text("",14f); body.addView(error)
        body.addView(ui.button("Aplicar filtros") { if(draft.start!=null && draft.end!=null && draft.start!!>draft.end!!) error.text="La fecha inicial no puede ser posterior a la final." else { setFilter(draft); dispose() } })
        body.addView(ui.button("Limpiar filtros",false) { setFilter(ConsultationFilter(query=filter.query)); dispose() })
        body.addView(ui.button("Cancelar",false) { dispose() })
    }
    fun dispose() { sheet?.dismiss(); sheet=null; detailBody=null; detailRecord=null }
    private inner class Cards: RecyclerView.Adapter<Cards.Holder>() {
        var rows=emptyList<JsonObject>()
        inner class Holder(val card: com.google.android.material.card.MaterialCardView,val body: LinearLayout): RecyclerView.ViewHolder(card)
        override fun getItemCount()=rows.size
        override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
            val card=ui.card().apply { layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=ui.dp(12) }; isClickable=true; isFocusable=true }
            val body=ui.column(); card.addView(body); return Holder(card,body)
        }
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val data=rows[position]; val body=holder.body; body.removeAllViews()
            when(route) {
                "moras" -> moraBody(body,ConsultationPresentation.mora(data,vm.consultationCatalogs.value))
                "notificaciones" -> noticeBody(body,ConsultationPresentation.notice(data,vm.consultationCatalogs.value))
                else -> {
                    val row=ConsultationPresentation.audit(data,vm.consultationCatalogs.value)
                    body.addView(ui.text(row.user,20f)); body.addView(ui.text(row.action,22f)); body.addView(ui.text(row.entity,16f,true))
                    if(row.target.isNotBlank()) body.addView(ui.text(row.target,18f))
                    if(row.description.isNotBlank() && !row.technicalDescription) body.addView(ui.text(row.description,14f,true).apply { maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END })
                    body.addView(ui.text(P.dateTime(I.text(data,"Fecha")),14f,true))
                }
            }
            body.addView(ui.text("Ver detalle",14f).apply { gravity=Gravity.END; setTextColor(ui.color(R.color.login_cyan)) })
            holder.card.setOnClickListener { detail(data) }
        }
    }
}
