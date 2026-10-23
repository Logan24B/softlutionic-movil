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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application): AndroidViewModel(application) {
 private val session = SessionManager(application)
 private val repository = SoftlutionicRepository(createApi(session),session,Room.databaseBuilder(application,CacheDatabase::class.java,"softlutionic-cache.db").build().cache())
 private val selected = MutableStateFlow("")
 private val connectivity = application.getSystemService(android.net.ConnectivityManager::class.java)
 private val callback = object: android.net.ConnectivityManager.NetworkCallback() {
  override fun onAvailable(network: android.net.Network) { if(session.authenticated.value && selected.value.isNotEmpty()) refresh() }
 }
 init { connectivity.registerDefaultNetworkCallback(callback) }
 override fun onCleared() { connectivity.unregisterNetworkCallback(callback); super.onCleared() }
 val authenticated = session.authenticated
 val busy = MutableStateFlow(false)
 val message = MutableStateFlow("")
 val modules = MutableStateFlow<List<Module>>(emptyList())
 val content = selected.flatMapLatest { repository.observe(it) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
 fun runTask(block: suspend () -> Unit) { viewModelScope.launch { busy.value=true; try { block() } catch(e: Exception) {
  if(e is CancellationException) throw e
  message.value=when(e) {
   is retrofit2.HttpException -> when(e.code()) { 401 -> { repository.logout(); "Sesión no válida. Inicie sesión nuevamente." }; 403 -> "No tiene permiso para esta operación."; 404 -> "Recurso no disponible; compruebe que la API móvil esté desplegada."; 409 -> "Registro duplicado o con dependencias."; 400,422 -> e.response()?.errorBody()?.string() ?: "Revise los datos."; else -> "Error del servidor (${e.code()})." }
   is java.net.SocketTimeoutException -> "Tiempo de espera agotado. Puede consultar la caché."
   is java.io.IOException -> "Sin conexión. Se muestran los últimos datos guardados."
   else -> e.message ?: "No se pudo completar la operación."
  }
 } finally { busy.value=false } } }
 fun login(user: String,password: String) = runTask { repository.login(user,password); load() }
 suspend fun load() { repository.refreshIdentity(); modules.value=repository.schema() }
 fun start() = runTask { try { load() } catch(e: java.io.IOException) { modules.value=repository.schema(); message.value="Sin conexión: datos guardados" } }
 fun select(name: String) { selected.value=name; refresh() }
 fun refresh() = runTask { repository.refresh(selected.value); message.value="Actualizado" }
 fun prepareForm(module: Module, ready: (Map<String,List<com.google.gson.JsonObject>>,List<com.google.gson.JsonObject>) -> Unit) = runTask {
  val relations=mutableMapOf<String,List<com.google.gson.JsonObject>>()
  for(field in module.fields.filter { it.relation!=null }) {
   val relation=field.relation!!
   if(relation !in relations) relations[relation]=repository.options(relation)
  }
  val services=if(module.name=="facturas") repository.options("servicios") else emptyList()
  ready(relations,services)
 }
 fun save(module: String,id: String?,payload: com.google.gson.JsonObject,success: ()->Unit,finished: ()->Unit) = runTask {
  try { repository.save(module,id,payload); success() } finally { finished() }
 }
 fun delete(module: String,id: String) = runTask { repository.delete(module,id) }
 fun logout() = runTask { repository.logout(); modules.value=emptyList() }
}
