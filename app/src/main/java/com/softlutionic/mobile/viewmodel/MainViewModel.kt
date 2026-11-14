package com.softlutionic.mobile.viewmodel
import android.app.Application
import androidx.lifecycle.*
import androidx.room.Room
import com.softlutionic.mobile.data.*
import com.softlutionic.mobile.data.local.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.softlutionic.mobile.ui.clients.ClientFilters
import com.softlutionic.mobile.ui.services.ServiceListState
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application): AndroidViewModel(application) {
 private val graph=AppGraph.get(application)
 private val session = graph.session
 private val repository = graph.repository
 private val analysisStore: com.softlutionic.mobile.ui.analysis.AnalysisStore = com.softlutionic.mobile.ui.analysis.AnalysisStore(viewModelScope) { filters ->
  try { repository.currentIdentity.filterNotNull().first(); repository.analytics(filters) }
  catch (e: retrofit2.HttpException) { if (e.code() == 401) logout(); throw e }
 }
 val analysisState = analysisStore.state
 fun loadAnalysis(filters: AnalyticsFilters = analysisState.value.filters) = analysisStore.load(filters)
 fun openAnalysis() { if (!analysisState.value.loaded && !analysisState.value.loading) loadAnalysis() }
 init { viewModelScope.launch { session.authenticated.collect { if (!it) analysisStore.clear() } } }
 private val selected = MutableStateFlow("")
 val period = MutableStateFlow<Pair<String,String>?>(null)
 val dashboardAllowed = MutableStateFlow(false)
 val recordCatalogs = MutableStateFlow<Map<String,List<com.google.gson.JsonObject>>>(emptyMap())
 private var recordCatalogJob: Job? = null
 fun loadRecordCatalogs(module: Module) {
  recordCatalogJob?.cancel()
  recordCatalogJob=viewModelScope.launch {
   val readable=modules.value.filter { effectiveModule(it).permissions.read }
   val direct=module.fields.mapNotNull { it.relation }.toSet()
   for(definition in readable) {
    try {
     val values=repository.cachedOptions(definition.name)
     if(values.isNotEmpty()) recordCatalogs.value=recordCatalogs.value+(definition.name to values)
     else if(definition.name in direct && definition.name!=module.name)
      recordCatalogs.value=recordCatalogs.value+(definition.name to repository.refreshOptions(definition.name))
    } catch(e: Exception) { if(e is CancellationException) throw e /* Keep human fallbacks and cached data. */ }
   }
  }
 }
 private val connectivity = application.getSystemService(android.net.ConnectivityManager::class.java)
 private val callback = object: android.net.ConnectivityManager.NetworkCallback() {
  override fun onAvailable(network: android.net.Network) { if(session.authenticated.value && selected.value.isNotEmpty()) refresh() }
 }
 init { connectivity.registerDefaultNetworkCallback(callback) }
 override fun onCleared() { connectivity.unregisterNetworkCallback(callback); super.onCleared() }
 val currentIdentity = repository.currentIdentity
 fun effectiveModule(module: Module) = com.softlutionic.mobile.domain.auth.AuthorizationPolicy.effective(currentIdentity.value,module)
 val authenticated = session.authenticated
 init { viewModelScope.launch { authenticated.collect { if(!it) repository.forgetIdentity() } } }
 val busy = MutableStateFlow(false)
 val message = MutableStateFlow("")
 val modules = MutableStateFlow<List<Module>>(emptyList())
 val consultationCatalogs=MutableStateFlow<Map<String,List<com.google.gson.JsonObject>>>(emptyMap())
 val consultationFilters=MutableStateFlow<Map<String,com.softlutionic.mobile.ui.consultation.ConsultationFilter>>(emptyMap())
 val consultationNotice=MutableStateFlow("")
 val noticeSaving=MutableStateFlow<String?>(null)
 val noticeLoadError=MutableStateFlow("")
 val contactQuery=MutableStateFlow("")
 val contactUiState=MutableStateFlow(com.softlutionic.mobile.ui.contact.ContactUiState())
 fun enableDeviceNotices(showResult: Boolean=true) {
  if(!com.softlutionic.mobile.notifications.DeviceNotices.allowed(getApplication()) || session.access==null) return
  com.google.firebase.messaging.FirebaseMessaging.getInstance().token
   .addOnSuccessListener { token -> viewModelScope.launch {
    try {
     repository.registerPush(token)
     if(showResult) consultationNotice.value="Avisos del teléfono activados."
    } catch(e: Exception) {
     if(e is CancellationException) throw e
     if(showResult) consultationNotice.value=if(e is retrofit2.HttpException && e.code()==404)
      "Falta desplegar el registro de dispositivos en el backend."
     else "No se pudieron activar los avisos. Comprueba tu conexión y vuelve a intentar."
    }
   } }
   .addOnFailureListener { if(showResult) consultationNotice.value="No se pudo registrar este dispositivo en Firebase. Vuelve a intentar." }
 }
 val unreadNotices=MutableStateFlow<Int?>(null)
 init { viewModelScope.launch { graph.noticeEvents.collect {
  if(session.access!=null) try {
   repository.refresh("notificaciones")
   refreshUnreadNotices()
  } catch(e: Exception) {
   if(e is CancellationException) throw e
   consultationNotice.value="Llegó un aviso. Actualiza para consultar la información más reciente."
  }
 } } }
 private suspend fun refreshUnreadNotices() {
  unreadNotices.value=null
  try { unreadNotices.value=repository.unreadNoticeCount() }
  catch(e: Exception) {
   if(e is CancellationException) throw e
   consultationNotice.value="No se pudo actualizar el contador de no leídas. Vuelve a actualizar."
  }
 }
 private var consultationJob: Job?=null
 private var noticeJob: Job?=null
 private val consultationLoaded=mutableSetOf<String>()
 fun loadConsultationCatalogs(name: String,force: Boolean=false) {
  if(!force && name in consultationLoaded) return
  consultationJob?.cancel()
  consultationJob=viewModelScope.launch {
   consultationNotice.value=""
   val routes=when(name) {
    "moras" -> listOf("facturas","clientes","contratos","estados-factura")
    "notificaciones" -> emptyList()
    "auditoria" -> listOf("facturas","clientes","pagos","usuarios","contratos","servicios")
    else -> emptyList()
   }
   for(route in routes) {
    val definition=modules.value.find { it.name==route } ?: continue
    if(!com.softlutionic.mobile.ui.consultation.ConsultationPresentation.canRead(currentIdentity.value,definition)) continue
    try {
     val cached=repository.cachedOptions(route)
     if(cached.isNotEmpty()) consultationCatalogs.value=consultationCatalogs.value+(route to cached)
     if(force || route !in consultationCatalogs.value) consultationCatalogs.value=consultationCatalogs.value+(route to repository.refreshOptions(route))
    } catch(e: Exception) { if(e is CancellationException) throw e; consultationNotice.value="Algunas relaciones no se pudieron actualizar. Se conservan los datos guardados." }
   }
   consultationLoaded.add(name)
  }
 }
 fun markNoticeRead(id: String) {
  if(noticeSaving.value!=null) return
  val definition=modules.value.find { it.name=="notificaciones" }?.let { effectiveModule(it) } ?: return
  if(!com.softlutionic.mobile.ui.consultation.ConsultationPresentation.canMarkRead(definition)) { consultationNotice.value="No tienes permiso para marcar avisos como leídos."; return }
  if(!invoiceConnected()) { consultationNotice.value="Sin conexión. Conéctate para marcar el aviso como leído."; return }
  noticeSaving.value=id
  noticeJob=viewModelScope.launch {
   try {
    repository.markNoticeRead(id)
    com.softlutionic.mobile.notifications.DeviceNotices.cancel(getApplication(),id)
    consultationNotice.value="Notificación marcada como leída."
    refreshUnreadNotices()
   }
   catch(e: Exception) { if(e is CancellationException) throw e; consultationNotice.value=when(e) {
    is retrofit2.HttpException -> when(e.code()) {
     401 -> "Tu sesión no es válida. Inicia sesión nuevamente."
     403 -> "No tienes permiso para modificar este aviso."
     404 -> "La notificación ya no está disponible. Actualiza el listado."
     else -> "No se pudo confirmar el estado del aviso (${e.code()}). Actualiza para comprobarlo."
    }
    is java.io.IOException -> "Sin conexión. No se confirmó el cambio; actualiza para comprobar el estado."
    else -> e.message ?: "No se pudo actualizar el aviso."
   } } finally { noticeSaving.value=null }
  }
 }
 val paymentCatalogs=MutableStateFlow<Map<String,List<com.google.gson.JsonObject>>>(emptyMap())
 val paymentFilters=MutableStateFlow(com.softlutionic.mobile.ui.payments.PaymentFilters())
 val paymentSaving=MutableStateFlow(false)
 val paymentLoading=MutableStateFlow(false)
 val paymentNotice=MutableStateFlow("")
 val paymentError=MutableStateFlow("")
 val paymentSaved=MutableStateFlow<com.google.gson.JsonObject?>(null)
 private var paymentCatalogJob: Job?=null
 private var paymentSaveJob: Job?=null
 private var paymentConnection: ()->Boolean = { invoiceConnected() }
 fun loadPaymentCatalogs() {
  if(paymentCatalogJob?.isActive==true) return
  paymentCatalogJob=viewModelScope.launch {
   paymentLoading.value=true; paymentNotice.value=""
   try {
    for(route in listOf("pagos","facturas","estados-factura","contratos","clientes","tipos-contrato")) {
     if(modules.value.none { it.name==route && effectiveModule(it).permissions.read }) continue
     try {
      val cached=repository.cachedOptions(route)
      if(cached.isNotEmpty()) paymentCatalogs.value=paymentCatalogs.value+(route to cached)
      paymentCatalogs.value=paymentCatalogs.value+(route to repository.refreshOptions(route))
     } catch(e: Exception) { if(e is CancellationException) throw e; paymentNotice.value="No se pudo actualizar toda la información. Se muestran datos guardados." }
    }
    try { repository.reconcilePayment()?.let { paymentNotice.value="Se verificó en el servidor el pago que estaba sin confirmación." } }
    catch(e: Exception) { if(e is CancellationException) throw e }
   } finally { paymentLoading.value=false }
  }
 }
 fun savePayment(invoiceId: String,existing: com.google.gson.JsonObject?,receipt: String,transfer: Boolean,amount: String?=null,confirmed: Boolean=true,date: String?=null) {
  if(paymentSaving.value) return
  val definition=modules.value.find { it.name=="pagos" }?.let { effectiveModule(it) } ?: return
  val id=existing?.let { com.softlutionic.mobile.ui.invoices.InvoicePresentation.text(it,"id") }
  if(!(if(id==null) definition.permissions.create else definition.permissions.update)) { paymentError.value="No tienes permiso para esta operación."; return }
  if(!paymentConnection()) { paymentError.value="Sin conexión. Conéctate para registrar el pago."; return }
  if(listOf("facturas","estados-factura","pagos").any { route -> modules.value.none { it.name==route && effectiveModule(it).permissions.read } }) {
   paymentError.value="No tienes permiso para consultar los datos necesarios del pago."; return
  }
  if(existing==null && !invoiceTimeSource.status().ready) { paymentError.value="Activa la fecha, hora y zona horaria automáticas del dispositivo."; return }
  val instant=invoiceTimeSource.capture().captured
  paymentSaving.value=true; paymentError.value=""
  paymentSaveJob=viewModelScope.launch {
   try {
    val i=com.softlutionic.mobile.ui.invoices.InvoicePresentation
    val p=com.softlutionic.mobile.ui.payments.PaymentPresentation
    val invoices=repository.refreshOptions("facturas")
    val states=repository.refreshOptions("estados-factura")
    val payments=repository.refreshOptions("pagos")
    paymentCatalogs.value=paymentCatalogs.value+mapOf("facturas" to invoices,"pagos" to payments,"estados-factura" to states)
    val invoice=invoices.singleOrNull { i.text(it,"id")==invoiceId } ?: error("La factura ya no está disponible.")
    val row=p.invoice(invoice,paymentCatalogs.value)
    val payload=if(existing==null) {
     check(payments.none { i.text(it,"FacturaId")==invoiceId }) { "Esta factura ya tiene un pago. Abre su detalle para editarlo o confirmar su estado." }
     p.payload(definition,row,receipt,transfer,instant)
    } else {
     val target=payments.singleOrNull { i.text(it,"id")==id } ?: error("El pago ya no está disponible.")
     check(i.text(target,"FacturaId")==invoiceId) { "La factura asociada al pago cambió. Actualiza el historial." }
     val value=amount?.toBigDecimalOrNull() ?: error("Introduce un monto válido.")
     check(value.signum()>=0 && value.scale()<=2 && value.precision()<=18) { "Introduce un monto no negativo con hasta dos decimales." }
     check(receipt.toIntOrNull()?.let { it>0 }==true) { "Introduce un número de recibo entero mayor que cero." }
     if(date!=null) check(runCatching { java.time.OffsetDateTime.parse(date) }.isSuccess) { "Selecciona una fecha y hora válidas." }
     com.google.gson.JsonObject().apply {
      if(date!=null && definition.fields.any { it.name=="Fecha_Pago" && !it.read_only }) addProperty("Fecha_Pago",date)
      if(definition.fields.any { it.name=="Monto_Pagado" && !it.read_only }) addProperty("Monto_Pagado",value)
      if(definition.fields.any { it.name=="NumeroRecibo" && !it.read_only }) addProperty("NumeroRecibo",receipt.toInt())
      if(definition.fields.any { it.name=="MetodoPago" && !it.read_only }) addProperty("MetodoPago",transfer)
      if(definition.fields.any { it.name=="EstadoPago" && !it.read_only }) addProperty("EstadoPago",confirmed)
     }
    }
    val saved=repository.savePayment(id,payload)
    paymentCatalogs.value=paymentCatalogs.value+("pagos" to (payments.filter { it["id"]!=saved["id"] }+saved))
    i.nested(saved,"factura_detalle")?.let { updated ->
     paymentCatalogs.value=paymentCatalogs.value+("facturas" to (invoices.filter { it["id"]!=updated["id"] }+updated))
    }
    message.value=if(existing==null) "Pago registrado correctamente." else "Pago actualizado correctamente."
    paymentSaved.value=saved
    // Backend owns invoice state. Never PATCH EstadoId or calculate a remaining balance.
    try {
     paymentCatalogs.value=paymentCatalogs.value+("facturas" to repository.refreshOptions("facturas"))
     paymentCatalogs.value=paymentCatalogs.value+("pagos" to repository.refreshOptions("pagos"))
    } catch(e: Exception) { if(e is CancellationException) throw e; paymentNotice.value="Pago guardado. Pulsa Actualizar para sincronizar la factura." }
   } catch(e: Exception) {
    if(e is CancellationException) throw e
    paymentError.value=when(e) {
     is retrofit2.HttpException -> when(e.code()) {
      400,422 -> com.softlutionic.mobile.ui.invoices.InvoicePresentation.errors(e.response()?.errorBody()?.string()).values.joinToString("\n").ifBlank { "Revisa los datos del pago." }
      401 -> "La sesión venció. Inicia sesión nuevamente."
      403 -> "No tienes permiso para esta operación."
      409 -> "El recibo ya existe o el registro tiene dependencias. Actualiza el historial."
      else -> "No se pudo confirmar el pago. Revisa el historial antes de continuar."
     }
     is java.io.IOException -> "No se pudo confirmar la operación. Actualiza el historial y verifica el recibo. No se reenvió el pago."
     else -> e.message ?: "No se pudo completar la operación."
    }
   } finally { paymentSaving.value=false }
  }
 }
 // Client presentation state survives activity recreation; CRUD stays in the shared repository.
 val clientFilters = MutableStateFlow(ClientFilters())
 val clientDepartments = MutableStateFlow<List<com.google.gson.JsonObject>>(emptyList())
 val clientDepartmentsLoading = MutableStateFlow(false)
 val clientDepartmentsUnavailable = MutableStateFlow(false)
 val clientSaving = MutableStateFlow(false)
 val serviceListState = MutableStateFlow(ServiceListState())
 val userListState = MutableStateFlow(com.softlutionic.mobile.ui.users.UserListState())
 val userSaving = MutableStateFlow(false)
 val serviceSaving = MutableStateFlow(false)
 val contractSaving = MutableStateFlow(false)
 val contractTypeSaving = MutableStateFlow(false)
 val contractTypeQuery = MutableStateFlow("")
 val invoiceSaving = MutableStateFlow(false)
 val invoiceFilters = MutableStateFlow(com.softlutionic.mobile.ui.invoices.InvoiceFilters())
 val invoiceDraft = MutableStateFlow<com.softlutionic.mobile.ui.invoices.InvoiceDraft?>(null)
 val invoiceErrors = MutableStateFlow<Map<String,String>>(emptyMap())
 val invoiceSaved = MutableStateFlow<com.google.gson.JsonObject?>(null)
 val invoiceSaveMessage = MutableStateFlow("")
 private var invoiceTimeSource: com.softlutionic.mobile.ui.invoices.InvoiceTimeSource=com.softlutionic.mobile.ui.invoices.DeviceInvoiceTime(application)
 val invoiceClockWarning = MutableStateFlow(false)
 val invoiceRetryReady = MutableStateFlow(false)
 val invoiceSubmissionEmission = MutableStateFlow<com.softlutionic.mobile.ui.invoices.InvoiceEmission?>(null)
 fun previewInvoiceEmission()=invoiceSubmissionEmission.value ?: invoiceTimeSource.capture()
 val invoiceCatalogs = MutableStateFlow<Map<String,List<com.google.gson.JsonObject>>>(emptyMap())
 val invoiceCatalogsLoading = MutableStateFlow(false)
 val invoiceCatalogsUnavailable = MutableStateFlow(false)
 private var invoiceCatalogJob: Job?=null
 fun loadInvoiceCatalogs(force: Boolean=false) {
  if(invoiceCatalogJob?.isActive==true || (!force && invoiceCatalogs.value.isNotEmpty())) return
  invoiceCatalogJob=viewModelScope.launch {
   invoiceCatalogsLoading.value=true; invoiceCatalogsUnavailable.value=false
   try {
    for(route in listOf("contratos","clientes","tipos-contrato","servicios","estados-factura")) {
     if(modules.value.none { it.name==route && effectiveModule(it).permissions.read }) continue
     try {
      val saved=repository.cachedOptions(route)
      if(saved.isNotEmpty()) invoiceCatalogs.value=invoiceCatalogs.value+(route to saved)
      invoiceCatalogs.value=invoiceCatalogs.value+(route to repository.refreshOptions(route))
     } catch(e: Exception) { if(e is CancellationException) throw e; invoiceCatalogsUnavailable.value=true }
    }
   } finally { invoiceCatalogsLoading.value=false }
  }
 }
 fun invoiceConnected(): Boolean {
  val network=connectivity.activeNetwork ?: return false
  return connectivity.getNetworkCapabilities(network)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
 }
 fun beginInvoice(data: com.google.gson.JsonObject?=null) {
  if(invoiceSaving.value) return
  val definition=modules.value.find { it.name=="facturas" } ?: return
  if(!(if(data==null) effectiveModule(definition).permissions.create else effectiveModule(definition).permissions.update)) return
  invoiceErrors.value=emptyMap()
  invoiceClockWarning.value=false; invoiceSubmissionEmission.value=null; invoiceRetryReady.value=false
  invoiceDraft.value=if(data==null) com.softlutionic.mobile.ui.invoices.InvoiceDraft() else com.softlutionic.mobile.ui.invoices.InvoicePresentation.draft(data,definition,invoiceCatalogs.value)
 }
 fun saveInvoice() {
  if(invoiceSaving.value) return
  val draft=invoiceDraft.value ?: return
  val definition=modules.value.find { it.name=="facturas" } ?: return
  val policy=com.softlutionic.mobile.domain.auth.AuthorizationPolicy
  if(!policy.allowsWrite(currentIdentity.value,definition,if(draft.id==null) "create" else "update")) {
   invoiceErrors.value=mapOf("detail" to "No tienes permiso para modificar facturas."); return
  }
  if(draft.id==null && !invoiceTimeSource.status().ready) { invoiceClockWarning.value=true; return }
  val errors=com.softlutionic.mobile.ui.invoices.InvoicePresentation.validate(draft,definition,invoiceCatalogs.value)
  if(errors.isNotEmpty()) { invoiceErrors.value=errors; return }
  if(!invoiceConnected()) { invoiceErrors.value=mapOf("detail" to "No hay conexión. Conéctate para generar la factura."); return }
  val retry=invoiceRetryReady.value
  // Capture synchronously at the button action, before Room or network suspension.
  val clickedEmission=if(draft.id==null) invoiceTimeSource.capture() else null
  invoiceSaving.value=true; invoiceErrors.value=emptyMap(); invoiceRetryReady.value=false
  var invoicePostStage=false
  runTask {
   try {
    val pending=if(draft.id==null) repository.pendingInvoice() else null
    if(pending!=null && pending["CodigoFact"]?.asString!=draft.values["CodigoFact"])
     throw com.softlutionic.mobile.data.repository.InvoiceUncertain("Existe un envío pendiente para la factura ${pending["CodigoFact"]?.asString}. Introduce ese código y comprueba su resultado antes de generar una nueva.")
    // One instant at submission, never the time captured when the form was opened.
    val emission=if(pending!=null) com.softlutionic.mobile.ui.invoices.InvoiceEmission(java.time.ZonedDateTime.of(java.time.LocalDate.parse(pending["Fecha_Emision"].asString),java.time.LocalTime.parse(pending["Hora_Emision"].asString),java.time.ZoneId.systemDefault())) else if(draft.id==null) clickedEmission!! else com.softlutionic.mobile.ui.invoices.InvoiceEmission(
     java.time.ZonedDateTime.of(java.time.LocalDate.parse(draft.values.getValue("Fecha_Emision")),java.time.LocalTime.parse(draft.values.getValue("Hora_Emision")),java.time.ZoneId.systemDefault()))
    invoiceSubmissionEmission.value=emission
    val type=com.softlutionic.mobile.ui.invoices.InvoicePresentation.byId(invoiceCatalogs.value,"tipos-contrato",draft.typeId) ?: error("Selecciona un tipo de contrato.")
    val contract=if(pending!=null) {
     val original=repository.options("contratos").singleOrNull { it["id"]==pending["ContratoId"] }
      ?: throw com.softlutionic.mobile.data.repository.InvoiceUncertain("No se pudo verificar el contrato del envío pendiente. No se reenvió la factura.")
     if(original["ClienteId"]?.asString!=draft.clientId || original["TipoContratoId"]?.asString!=draft.typeId)
      throw com.softlutionic.mobile.data.repository.InvoiceUncertain("El envío pendiente corresponde a otro cliente o tipo. Restaura esos datos para comprobar su resultado antes de reintentar.")
     original
    } else repository.resolveInvoiceContract(draft.clientId,type,emission)
    invoiceCatalogs.value=invoiceCatalogs.value+("contratos" to (invoiceCatalogs.value["contratos"].orEmpty().filter { it["id"]!=contract["id"] }+contract))
    val prepared=draft.copy(values=draft.values+mapOf("ContratoId" to contract["id"].asString,"Fecha_Emision" to emission.date,"Hora_Emision" to emission.time))
    invoicePostStage=true
    val result=repository.saveInvoice(draft.id,com.softlutionic.mobile.ui.invoices.InvoicePresentation.payload(prepared,definition),retry)
    invoiceSaveMessage.value=if(result.refreshFailed) "Factura generada. No se pudo actualizar el listado automáticamente. Pulsa Actualizar." else if(draft.id==null) "Factura generada correctamente" else "Factura actualizada correctamente."
    invoiceDraft.value=null; invoiceSaved.value=result.invoice; message.value="Actualizado"
   } catch(e: Exception) {
    if(e is CancellationException) throw e
    invoiceErrors.value=when(e) {
     is com.softlutionic.mobile.data.repository.InvoiceUncertain -> { invoiceRetryReady.value=e.retryReady; mapOf("detail" to e.message.orEmpty()) }
     is retrofit2.HttpException -> if(e.code()==403) mapOf("detail" to "No tienes permiso para generar facturas.") else com.softlutionic.mobile.ui.invoices.InvoicePresentation.errors(e.response()?.errorBody()?.string()).ifEmpty { mapOf("detail" to if(e.code()==409) "Conflicto al generar la factura. Revisa el código y el listado (HTTP 409)." else "El servidor rechazó la operación (HTTP ${e.code()}). Revisa los datos.") }
     is java.io.IOException -> mapOf("detail" to if(invoicePostStage) "No se pudo comprobar el código antes del envío. No se envió la factura; revisa la conexión." else "No se pudo preparar el contrato. No se envió la factura; revisa la conexión.")
     else -> mapOf("detail" to (e.message ?: "No se pudo generar la factura."))
    }
    if(e is retrofit2.HttpException && e.code()==401) throw e
   } finally { invoiceSaving.value=false; invoiceSubmissionEmission.value=null }
  }
 }
 val contractFilters = MutableStateFlow(com.softlutionic.mobile.ui.contracts.ContractFilters())
 val contractCatalogs = MutableStateFlow<Map<String,List<com.google.gson.JsonObject>>>(emptyMap())
 val contractCatalogsLoading = MutableStateFlow(false)
 val contractCatalogsUnavailable = MutableStateFlow(false)
 private var contractCatalogJob: Job? = null
 fun loadContractCatalogs(module: Module, force: Boolean = false) {
  if(contractCatalogJob?.isActive==true || (!force && contractCatalogs.value.isNotEmpty())) return
  val routes=module.fields.mapNotNull { it.relation }.distinct()
  contractCatalogJob=viewModelScope.launch {
   contractCatalogsLoading.value=true
   contractCatalogsUnavailable.value=false
   try {
    for(route in routes) {
     val cached=repository.cachedOptions(route)
     if(cached.isNotEmpty()) contractCatalogs.value=contractCatalogs.value + (route to cached)
     try { contractCatalogs.value=contractCatalogs.value + (route to repository.refreshOptions(route)) }
     catch(e: Exception) { if(e is CancellationException) throw e; contractCatalogsUnavailable.value=true }
    }
   } finally { contractCatalogsLoading.value=false }
  }
 }
 private fun mutationState(module: String) = when(module) {
  "clientes" -> clientSaving
  "usuarios" -> userSaving
  "servicios" -> serviceSaving
  "contratos" -> contractSaving
  "tipos-contrato" -> contractTypeSaving
  "facturas" -> invoiceSaving
  else -> null
 }
 private var clientReferenceJob: Job? = null
 private fun clearClientPresentation() {
  contactQuery.value=""; contactUiState.value=com.softlutionic.mobile.ui.contact.ContactUiState()
  consultationJob?.cancel(); noticeJob?.cancel(); consultationLoaded.clear(); consultationCatalogs.value=emptyMap()
  consultationFilters.value=emptyMap(); consultationNotice.value=""; noticeSaving.value=null; noticeLoadError.value=""
  paymentSaveJob?.cancel()
  paymentCatalogJob?.cancel(); paymentCatalogs.value=emptyMap(); paymentFilters.value=com.softlutionic.mobile.ui.payments.PaymentFilters()
  paymentError.value=""; paymentNotice.value=""; paymentSaved.value=null
  invoiceCatalogJob?.cancel(); invoiceCatalogs.value=emptyMap(); invoiceDraft.value=null
  invoiceErrors.value=emptyMap(); invoiceSaved.value=null; invoiceFilters.value=com.softlutionic.mobile.ui.invoices.InvoiceFilters()
  invoiceClockWarning.value=false; invoiceSubmissionEmission.value=null
  invoiceCatalogsLoading.value=false; invoiceCatalogsUnavailable.value=false
  recordCatalogJob?.cancel(); recordCatalogs.value=emptyMap()
  contractTypeQuery.value=""
  contractCatalogJob?.cancel(); contractCatalogs.value=emptyMap()
  contractFilters.value=com.softlutionic.mobile.ui.contracts.ContractFilters()
  contractCatalogsUnavailable.value=false; contractCatalogsLoading.value=false
  clientReferenceJob?.cancel()
  clientDepartments.value=emptyList(); clientFilters.value=ClientFilters()
  clientDepartmentsLoading.value=false; clientDepartmentsUnavailable.value=false
  userListState.value=com.softlutionic.mobile.ui.users.UserListState()
  serviceListState.value=ServiceListState()
 }
 fun loadClientDepartments(module: Module, force: Boolean = false) {
  val route = module.fields.find { it.name == "DepartamentoId" }?.relation ?: return
  if(clientReferenceJob?.isActive == true || (!force && clientDepartments.value.isNotEmpty())) return
  clientReferenceJob = viewModelScope.launch {
   clientDepartmentsLoading.value = true
   try {
    val cached = repository.cachedOptions(route)
    if(cached.isNotEmpty()) clientDepartments.value = cached
    clientDepartments.value = repository.refreshOptions(route)
    clientDepartmentsUnavailable.value = false
   } catch(e: Exception) {
    if(e is CancellationException) throw e
    clientDepartmentsUnavailable.value = true
   } finally { clientDepartmentsLoading.value = false }
  }
 }
 val content = combine(selected,period) { name,range -> repository.contentKey(name,range?.first,range?.second) }
  .flatMapLatest { repository.observe(it) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
 fun runTask(block: suspend () -> Unit) { viewModelScope.launch { busy.value=true; try { block() } catch(e: Exception) {
  if(e is CancellationException) throw e
  message.value=when(e) {
   is retrofit2.HttpException -> when(e.code()) { 401 -> { clearClientPresentation(); repository.logout(); "Sesión no válida. Inicie sesión nuevamente." }; 403 -> "No tiene permiso para esta operación."; 404 -> "Recurso no disponible; compruebe que la API móvil esté desplegada."; 409 -> "Registro duplicado o con dependencias."; 400,422 -> e.response()?.errorBody()?.string() ?: "Revise los datos."; else -> "Error del servidor (${e.code()})." }
   is java.net.SocketTimeoutException -> "Tiempo de espera agotado. Puede consultar la caché."
   is java.io.IOException -> "Sin conexión. Se muestran los últimos datos guardados."
   else -> e.message ?: "No se pudo completar la operación."
  }
 } finally { busy.value=false } } }
 fun login(user: String,password: String) = runTask { clearClientPresentation(); repository.login(user,password); load() }
 private suspend fun readDashboardPermission() {
  dashboardAllowed.value=runCatching {
   com.google.gson.JsonParser.parseString(repository.cached("identity") ?: "{}").asJsonObject["dashboard"]?.asBoolean==true
  }.getOrDefault(false)
 }
 suspend fun load() { repository.restoreIdentity(); repository.refreshIdentity(); readDashboardPermission(); modules.value=repository.schema(); enableDeviceNotices(false) }
 fun start() = runTask { try { load() } catch(e: java.io.IOException) { repository.restoreIdentity(); readDashboardPermission(); modules.value=repository.schema(); message.value="Sin conexión: datos guardados" } }
 fun select(name: String) {
  if(name==ContactRequestDto.MODULE && modules.value.find { it.name==name }?.let { effectiveModule(it).permissions.read }!=true) return
  if(name=="usuarios" && !com.softlutionic.mobile.domain.auth.AuthorizationPolicy.canReadUsers(currentIdentity.value)) return
  selected.value=name; refresh()
 }
 fun refresh() = if(selected.value=="analisis") Unit else runTask {
  val name=selected.value
  if(name.isNotEmpty()) {
   if(name=="pagos") loadPaymentCatalogs()
   if(name in com.softlutionic.mobile.ui.consultation.ConsultationPresentation.routes) loadConsultationCatalogs(name,true)
   val range=period.value
   if(name==ContactRequestDto.MODULE) contactUiState.value=com.softlutionic.mobile.ui.contact.ContactUiState()
   if(name=="notificaciones") { noticeLoadError.value=""; unreadNotices.value=null }
   try { repository.refresh(name,range?.first,range?.second) }
   catch(e: Exception) {
    if(e is CancellationException) throw e
    if(name==ContactRequestDto.MODULE) {
     val code=(e as? retrofit2.HttpException)?.code()
     val error=when(code) {
      401 -> "Tu sesión no es válida. Inicia sesión nuevamente."
      403 -> "No tienes permiso para consultar solicitudes de contacto."
      404 -> "El módulo Contacto todavía no está disponible en el servidor."
      503 -> "Servicio de solicitudes de contacto temporalmente no disponible."
      else -> if(e is java.io.IOException) "No se pudieron cargar las solicitudes. Comprueba tu conexión y vuelve a intentar." else "No se pudieron cargar las solicitudes."
     }
     contactUiState.value=com.softlutionic.mobile.ui.contact.ContactUiState(com.softlutionic.mobile.ui.contact.ContactPhase.ERROR,error,code==401 || code==403)
    }
    if(name=="notificaciones") noticeLoadError.value=when(e) {
     is retrofit2.HttpException -> when(e.code()) {
      401 -> "Tu sesión no es válida. Inicia sesión nuevamente."
      403 -> "No tienes permiso para consultar notificaciones."
      404 -> "El recurso de notificaciones no está disponible."
      503 -> "Servicio de notificaciones temporalmente no disponible. Vuelve a intentar en unos momentos."
      else -> "No se pudieron consultar las notificaciones (${e.code()})."
     }
     is java.io.IOException -> "No se pudieron actualizar las notificaciones. Comprueba tu conexión y vuelve a intentar."
     else -> "No se pudo interpretar la respuesta de notificaciones."
    }
    throw e
   }
   if(name=="notificaciones") refreshUnreadNotices()
   if(name==ContactRequestDto.MODULE) contactUiState.value=com.softlutionic.mobile.ui.contact.ContactUiState(
    if(repository.cachedOptions(name).isEmpty()) com.softlutionic.mobile.ui.contact.ContactPhase.EMPTY else com.softlutionic.mobile.ui.contact.ContactPhase.SUCCESS)
   message.value="Actualizado"
  }
 }
 fun filterPeriod(range: Pair<String,String>?) { period.value=range; refresh() }
 fun prepareForm(module: Module, ready: (Map<String,List<com.google.gson.JsonObject>>,List<com.google.gson.JsonObject>) -> Unit) = runTask {
  val relations=mutableMapOf<String,List<com.google.gson.JsonObject>>()
  for(field in module.fields.filter { it.relation!=null }) {
   val relation=field.relation!!
   if(relation !in relations) relations[relation]=repository.options(relation)
  }
  val services=if(module.name=="facturas") repository.options("servicios") else emptyList()
  ready(relations,services)
 }
 private fun localMutationDenied(name: String, operation: String): com.softlutionic.mobile.domain.auth.AuthorizationDenied? {
  val policy=com.softlutionic.mobile.domain.auth.AuthorizationPolicy
  if(name !in policy.employeeReadOnlyModules) return null
  val definition=modules.value.find { it.name==name }
  return if(definition!=null && policy.allowsWrite(currentIdentity.value,definition,operation)) null
   else com.softlutionic.mobile.domain.auth.AuthorizationDenied(name)
 }
 fun save(module: String,id: String?,payload: com.google.gson.JsonObject,success: ()->Unit,finished: ()->Unit,failure: ((Exception)->Unit)? = null) {
  localMutationDenied(module,if(id==null) "create" else "update")?.let {
   message.value=it.message.orEmpty(); failure?.invoke(it); finished(); return
  }
  val mutation = mutationState(module)
  if(mutation?.value==true) return
  mutation?.value=true
  runTask {
   try { repository.save(module,id,payload); if(mutation!=null) message.value="Actualizado"; success() }
   catch(e: Exception) {
    if(e is CancellationException || failure==null) throw e
    failure(e)
    if(e is retrofit2.HttpException && e.code()==401) throw e
   } finally { mutation?.value=false; finished() }
  }
 }
 fun delete(module: String,id: String,success: ()->Unit = {},finished: ()->Unit = {},failure: ((Exception)->Unit)? = null) {
  localMutationDenied(module,"delete")?.let {
   message.value=it.message.orEmpty(); failure?.invoke(it); finished(); return
  }
  val mutation = mutationState(module)
  if(mutation?.value==true) return
  mutation?.value=true
  runTask {
   try { repository.delete(module,id); if(mutation!=null) message.value="Actualizado"; success() }
   catch(e: Exception) {
    if(e is CancellationException || failure==null) throw e
    failure(e)
    if(e is retrofit2.HttpException && e.code()==401) throw e
   } finally { mutation?.value=false; finished() }
  }
 }
 fun logout() = runTask { analysisStore.clear(); clearClientPresentation(); repository.logout(); modules.value=emptyList(); dashboardAllowed.value=false; period.value=null; selected.value="" }
}
