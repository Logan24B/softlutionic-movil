package com.softlutionic.mobile.data.repository
import com.google.gson.*
import com.softlutionic.mobile.data.local.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.data.SessionManager
import kotlinx.coroutines.flow.map
class SoftlutionicRepository(private val api: Api, private val session: SessionManager, private val dao: CacheDao) {
 suspend fun options(module: String): List<JsonObject> = api.list(module).map { it.asJsonObject }
 fun observe(key: String) = dao.observe(key).map { it?.json }
 suspend fun cached(key: String) = dao.get(key)?.json
 suspend fun store(key: String, json: String) = dao.put(CacheEntity(key,json,System.currentTimeMillis()))
 suspend fun login(user: String,password: String) {
  val data = api.login(LoginRequest(user,password))
  dao.clear()
  session.save(data["access"].asString,data["refresh"].asString)
  store("identity",data.toString())
 }
 suspend fun refreshIdentity() { store("identity",api.me().toString()) }
 suspend fun schema(): List<Module> {
  return try { api.schema().also { store("schema",Gson().toJson(it)) } }
  catch(e: java.io.IOException) { cached("schema")?.let { Gson().fromJson(it,Array<Module>::class.java).toList() } ?: throw e }
 }
 suspend fun refresh(module: String) {
  val data = if(module == "dashboard") JsonObject().apply {
   add("facturacion",api.dashboard("facturacion")); add("comercial",api.dashboard("clientes-servicios"))
  }.toString() else api.list(module).toString()
  store(module,data)
 }
 suspend fun save(module: String,id: String?,data: JsonObject) {
  val saved = if(id == null) api.create(module,data) else api.update(module,id,data)
  val savedId = saved["id"]?.asString
  val items = cached(module)?.let { JsonParser.parseString(it).asJsonArray } ?: JsonArray()
  val updated = JsonArray()
  items.filter { it.asJsonObject["id"]?.asString != savedId }.forEach { updated.add(it) }
  updated.add(saved)
  store(module,updated.toString())
  try { refresh(module) } catch (_: java.io.IOException) { /* Server confirmed save; keep response cached. */ }
 }
 suspend fun delete(module: String,id: String) {
  api.delete(module,id)
  val items = cached(module)?.let { JsonParser.parseString(it).asJsonArray } ?: JsonArray()
  store(module,JsonArray().apply { items.filter { it.asJsonObject["id"]?.asString != id }.forEach { add(it) } }.toString())
  try { refresh(module) } catch (_: java.io.IOException) { /* Deletion was confirmed. */ }
 }
 suspend fun logout() { session.clear(); dao.clear() }
}
