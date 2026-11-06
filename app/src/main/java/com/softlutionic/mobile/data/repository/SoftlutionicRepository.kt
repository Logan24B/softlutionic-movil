package com.softlutionic.mobile.data.repository
import com.google.gson.*
import com.softlutionic.mobile.data.local.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.data.SessionManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import com.softlutionic.mobile.domain.auth.*
class SoftlutionicRepository(private val api: Api, private val session: SessionManager, private val dao: CacheDao) {
 suspend fun registerPush(token: String) {
  check(session.access!=null) { "Inicia sesión nuevamente." }
  val actor=currentIdentity.value ?: error("La identidad todavía no está disponible.")
  val body=JsonObject().apply {
   addProperty("token",token)
   session.pushToken?.takeIf { it!=token }?.let { addProperty("previous_token",it) }
  }
  val result=api.registerPush(body)
  check(result["user_id"]?.asString==actor.id && currentIdentity.value==actor && session.access!=null) { "La sesión cambió durante el registro de avisos." }
  session.savePush(token,actor.id)
 }
 private val paymentSubmission=PaymentSubmission(
  pending={ cached("payment-pending")?.let { JsonParser.parseString(it).takeIf { v -> v.isJsonObject }?.asJsonObject } },
  remember={ store("payment-pending",it?.toString() ?: "null") },
  list={ options("pagos") },
  write={ id,payload -> if(id==null) api.create("pagos",payload) else api.update("pagos",id,payload) })
 suspend fun reconcilePayment(): JsonObject?=paymentSubmission.reconcile()
 suspend fun savePayment(id: String?,payload: JsonObject): JsonObject {
  checkWrite("pagos",id,payload)
  val saved=paymentSubmission.submit(id,payload)
  try {
   val items=cachedOptions("pagos").filter { it["id"]!=saved["id"] }+saved
   store("pagos",Gson().toJson(items))
  } catch(_: Exception) { /* Server confirmed operation; keep returned data visible. */ }
  return saved
 }
 private val invoiceSubmission=InvoiceSubmission(
  pending={ pendingInvoice() },
  remember={ store("invoice-pending",it?.toString() ?: "null") },
  list={ options("facturas") }, post={ api.createInvoice(it) },
  cache={ saved ->
   val items=cachedOptions("facturas").filter { it["id"]!=saved["id"] }+saved
   store("facturas",Gson().toJson(items))
  }, refresh={ refresh("facturas") },
  log={ if(com.softlutionic.mobile.BuildConfig.DEBUG) android.util.Log.d("InvoiceSubmission",it) })
 suspend fun pendingInvoice(): JsonObject?=cached("invoice-pending")?.let { JsonParser.parseString(it).takeIf { value -> value.isJsonObject }?.asJsonObject }
 suspend fun saveInvoice(id: String?,data: JsonObject,allowRetry: Boolean=false): InvoiceReceipt {
  checkWrite("facturas",id,data)
  return if(id==null) invoiceSubmission.submit(data,allowRetry) else InvoiceReceipt(save("facturas",id,data))
 }
 private val identityState = MutableStateFlow<UserIdentity?>(null)
 val currentIdentity = identityState.asStateFlow()
 fun forgetIdentity() { identityState.value=null }
 suspend fun restoreIdentity() {
  identityState.value=if(session.access!=null) cached("identity")?.let { runCatching { AuthorizationPolicy.identity(JsonParser.parseString(it).asJsonObject) }.getOrNull() } else null
 }
 private fun checkRead(module: String) {
  if(module==ContactRequestDto.MODULE && (session.access==null || !AuthorizationPolicy.canReadUsers(identityState.value))) throw IllegalStateException("No tienes permiso para consultar solicitudes de contacto.")
  if(module=="auditoria" && (session.access==null || !AuthorizationPolicy.canReadUsers(identityState.value))) throw IllegalStateException("No tienes permiso para consultar auditoría.")
  if(module=="usuarios" && (session.access==null || !AuthorizationPolicy.canReadUsers(identityState.value))) throw AuthorizationDenied(module)
 }
 private fun auditSchemaReadable(json: String?): Boolean=runCatching {
  Gson().fromJson(json,Array<Module>::class.java).singleOrNull { it.name=="auditoria" }?.permissions?.read==true
 }.getOrDefault(false)
 private suspend fun checkAuditSchema(module: String) {
  if(module==ContactRequestDto.MODULE && !contactSchemaReadable(dao.get("schema")?.json)) throw IllegalStateException("No tienes permiso para consultar solicitudes de contacto.")
  if(module=="auditoria" && !auditSchemaReadable(dao.get("schema")?.json)) throw IllegalStateException("No tienes permiso para consultar auditoría.")
 }
 private suspend fun checkWrite(module: String, id: String?, payload: JsonObject?, deleting: Boolean=false) {
  if(module==ContactRequestDto.MODULE) throw IllegalStateException("Las solicitudes de contacto son de solo lectura.")
  if(module in setOf("moras","auditoria")) throw IllegalStateException("Este módulo es de solo lectura.")
  if(module !in AuthorizationPolicy.employeeReadOnlyModules && module !in listOf("usuarios","contratos","facturas","pagos","notificaciones")) return
  if(session.access==null) throw AuthorizationDenied(module)
  val schema=cached("schema")?.let { Gson().fromJson(it,Array<Module>::class.java).toList() }
  val definition=schema?.find { it.name==module } ?: throw AuthorizationDenied(module)
  if(module=="pagos" && payload!=null) {
   val editable=definition.fields.filter { !it.read_only }.map { it.name }.toSet()
   if(payload.keySet().any { it !in editable || it !in com.softlutionic.mobile.ui.payments.PaymentPresentation.writable }) throw AuthorizationDenied(module)
  }
  val operation=if(deleting) "delete" else if(id==null) "create" else "update"
  // Check module-level permission before even fetching a target.
  val caps=AuthorizationPolicy.effective(identityState.value,definition).permissions
  if(!(when(operation) { "delete" -> caps.delete; "create" -> caps.create; else -> caps.update })) throw AuthorizationDenied(module)
  // Use fresh server target data, never form-supplied role/ID or an old cached role.
  val target=if(module=="usuarios" && id!=null) api.list("usuarios").map { it.asJsonObject }.find { it["id"]?.asString==id } else null
  if(!AuthorizationPolicy.allowsWrite(identityState.value,definition,operation,target,payload)) throw AuthorizationDenied(module)
 }
 suspend fun options(module: String): List<JsonObject> = run { checkRead(module); checkAuditSchema(module); api.list(module).map { it.asJsonObject } }
 // Reference catalogs share Room with the existing lists and remain available offline.
 suspend fun cachedOptions(module: String): List<JsonObject> {
  checkRead(module)
  return cached(module)?.let {
  JsonParser.parseString(it).asJsonArray.map { value -> value.asJsonObject }
 } ?: emptyList()
 }
 suspend fun refreshOptions(module: String): List<JsonObject> = options(module).also { values ->
  store(module, Gson().toJson(values))
 }
 private fun contactSchemaReadable(json: String?)=runCatching {
  Gson().fromJson(json,Array<Module>::class.java).any { it.name==ContactRequestDto.MODULE && it.permissions.read }
 }.getOrDefault(false)
 fun observe(key: String) = if(key==ContactRequestDto.MODULE) combine(dao.observe(key),dao.observe("schema"),currentIdentity,session.authenticated) { entry,schema,actor,authenticated ->
  if(authenticated && AuthorizationPolicy.canReadUsers(actor) && contactSchemaReadable(schema?.json)) entry?.json else null
 } else if(key=="auditoria") combine(dao.observe(key),dao.observe("schema"),currentIdentity,session.authenticated) { entry, schema, actor, authenticated ->
  if(authenticated && AuthorizationPolicy.canReadUsers(actor) && auditSchemaReadable(schema?.json)) entry?.json else null
 } else combine(dao.observe(key),currentIdentity,session.authenticated) { entry, actor, authenticated ->
  if(key in setOf("usuarios","auditoria") && (!authenticated || !AuthorizationPolicy.canReadUsers(actor))) null else entry?.json
 }
 suspend fun cached(key: String): String? { checkRead(key); checkAuditSchema(key); return dao.get(key)?.json }
 suspend fun store(key: String, json: String) = dao.put(CacheEntity(key,json,System.currentTimeMillis()))
 suspend fun login(user: String,password: String) {
  val data = api.login(LoginRequest(user,password))
  session.clearPush()
  identityState.value=null
  dao.clear()
  session.save(data["access"].asString,data["refresh"].asString)
  // Identity used for authorization comes from auth/me, not JWT claims or login labels.
 }
 suspend fun refreshIdentity() {
  val data=api.me(); identityState.value=AuthorizationPolicy.identity(data); store("identity",data.toString())
 }
 suspend fun schema(): List<Module> {
  return try { api.schema().also { store("schema",Gson().toJson(it)) } }
  catch(e: java.io.IOException) { cached("schema")?.let { Gson().fromJson(it,Array<Module>::class.java).toList() } ?: throw e }
 }
 fun contentKey(module: String,start: String?,end: String?) = if(module=="dashboard" && start!=null && end!=null) "dashboard:$start:$end" else module
 suspend fun refresh(module: String,start: String? = null,end: String? = null) {
  checkRead(module)
  checkAuditSchema(module)
  val data = if(module == "dashboard") JsonObject().apply {
   add("facturacion",api.dashboard("facturacion",start,end)); add("comercial",api.dashboard("clientes-servicios",start,end))
  }.toString() else api.list(module).toString()
  store(contentKey(module,start,end),data)
 }
 suspend fun save(module: String,id: String?,data: JsonObject): JsonObject {
  if(module=="pagos") return savePayment(id,data)
  checkWrite(module,id,data)
  val saved = if(id == null) api.create(module,data) else api.update(module,id,data)
  val savedId = saved["id"]?.asString
  val items = cached(module)?.let { JsonParser.parseString(it).asJsonArray } ?: JsonArray()
  val updated = JsonArray()
  items.filter { it.asJsonObject["id"]?.asString != savedId }.forEach { updated.add(it) }
  updated.add(saved)
  store(module,updated.toString())
  try { refresh(module) } catch (_: java.io.IOException) { /* Server confirmed save; keep response cached. */ }
  catch(e: retrofit2.HttpException) { if(module !in listOf("clientes","servicios","usuarios","contratos","tipos-contrato","facturas","notificaciones")) throw e /* Write confirmed; preserve its response if the follow-up GET fails. */ }
  return saved
 }
 suspend fun markNoticeRead(id: String): JsonObject {
  val definition=schema().find { it.name=="notificaciones" }?.let { AuthorizationPolicy.effective(identityState.value,it) }
   ?: throw IllegalStateException("Notificaciones no está disponible.")
  check(com.softlutionic.mobile.ui.consultation.ConsultationPresentation.canMarkRead(definition)) { "No tienes permiso para marcar notificaciones como leídas." }
  check(session.access!=null) { "Inicia sesión nuevamente." }
  require(id.isNotBlank()) { "La notificación no tiene identificador." }
  val saved=api.readNotice(id)
  check(saved["id"]?.asString==id && saved["leida"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean==true) {
   "La respuesta no confirma la lectura del aviso. Actualiza para comprobar su estado."
  }
  val items=cachedOptions("notificaciones").toMutableList()
  val index=items.indexOfFirst { it["id"]?.asString==id }
  if(index>=0) items[index]=saved else items.add(saved)
  store("notificaciones",Gson().toJson(items))
  return saved
 }
 suspend fun unreadNoticeCount(): Int {
  val count=api.unreadNoticeCount()["count"]
  check(count!=null && count.isJsonPrimitive && count.asJsonPrimitive.isNumber) { "Contador de notificaciones no válido." }
  return count.asString.toIntOrNull()?.takeIf { it>=0 } ?: error("Contador de notificaciones no válido.")
 }
 suspend fun resolveInvoiceContract(clientId: String,type: JsonObject,emission: com.softlutionic.mobile.ui.invoices.InvoiceEmission): JsonObject {
  val schema=cached("schema")?.let { Gson().fromJson(it,Array<Module>::class.java).toList() }.orEmpty()
  val definition=schema.find { it.name=="contratos" } ?: throw AuthorizationDenied("contratos")
  if(!AuthorizationPolicy.effective(identityState.value,definition).permissions.read) throw AuthorizationDenied("contratos")
  // Fresh lookup; same active customer/type and ascending-ID choice used by the web.
  val contracts=refreshOptions("contratos")
  val existing=com.softlutionic.mobile.ui.invoices.InvoicePresentation.matchingContract(contracts,clientId,type["id"].asString)
  return existing ?: save("contratos",null,com.softlutionic.mobile.ui.invoices.InvoicePresentation.contractPayload(clientId,type,emission))
 }
 suspend fun delete(module: String,id: String) {
  checkWrite(module,id,null,true)
  api.delete(module,id)
  val items = cached(module)?.let { JsonParser.parseString(it).asJsonArray } ?: JsonArray()
  store(module,JsonArray().apply { items.filter { it.asJsonObject["id"]?.asString != id }.forEach { add(it) } }.toString())
  try { refresh(module) } catch (_: java.io.IOException) { /* Deletion was confirmed. */ }
  catch(e: retrofit2.HttpException) { if(module !in listOf("clientes","servicios","usuarios","contratos","tipos-contrato")) throw e /* Keep the confirmed local removal if list refresh fails. */ }
 }
 suspend fun logout() {
  val token=session.pushToken
  // Clear locally first: late data pushes are ignored even if remote revocation fails.
  identityState.value=null
  session.clearPush()
  try {
   if(token!=null) kotlinx.coroutines.withTimeout(5000) { api.unregisterPush(JsonObject().apply { addProperty("token",token) }) }
  } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e }
  finally { session.clear(); dao.clear() }
 }
}
