package com.softlutionic.mobile
import android.os.Bundle
import android.view.*
import android.widget.*
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.softlutionic.mobile.databinding.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.domain.model.Record
import com.softlutionic.mobile.ui.RecordAdapter
import com.softlutionic.mobile.viewmodel.MainViewModel
import com.google.gson.*
import kotlinx.coroutines.launch
class MainActivity: AppCompatActivity() {
 private lateinit var binding: ActivityMainBinding
 private lateinit var vm: MainViewModel
 private var recordsView: ViewRecordsBinding?=null
 private var module: Module?=null
 private var selected=""
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  binding=ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
  vm=ViewModelProvider(this)[MainViewModel::class.java]
  binding.toolbar.menu.add("Cerrar sesión").setOnMenuItemClickListener { vm.logout(); true }
  lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
   launch { vm.authenticated.collect { if(it) { showRecords(); vm.start() } else showLogin() } }
   launch { vm.busy.collect { binding.progress.visibility=if(it) View.VISIBLE else View.GONE; recordsView?.create?.isEnabled=!it; recordsView?.refresh?.isEnabled=!it } }
   launch { vm.message.collect { binding.status.text=it } }
   launch { vm.modules.collect { updateModules(it) } }
   launch { vm.content.collect { render(it) } }
  } }
 }
 private fun showLogin() {
  recordsView=null; binding.toolbar.menu.getItem(0).isVisible=false
  val login=ViewLoginBinding.inflate(layoutInflater,binding.content,false)
  binding.content.removeAllViews(); binding.content.addView(login.root)
  login.login.setOnClickListener {
   val user=login.username.text.toString().trim(); val password=login.password.text.toString()
   login.usernameLayout.error=if(user.isBlank()) "Ingrese su usuario" else null
   login.passwordLayout.error=if(password.isBlank()) "Ingrese su contraseña" else null
   if(user.isNotBlank() && password.isNotBlank() && !vm.busy.value) vm.login(user,password)
  }
 }
 private fun showRecords() {
  binding.toolbar.menu.getItem(0).isVisible=true
  val view=ViewRecordsBinding.inflate(layoutInflater,binding.content,false); recordsView=view
  binding.content.removeAllViews(); binding.content.addView(view.root)
  view.records.layoutManager=LinearLayoutManager(this)
  view.refresh.setOnClickListener { vm.refresh() }
  view.create.setOnClickListener { module?.let { edit(it,null) } }
  updateModules(vm.modules.value)
 }
 private fun updateModules(modules: List<Module>) {
  val view=recordsView ?: return
  val names=modules.map { it.name }.toMutableList()
  // Dashboard access is determined by the backend's admin-only user module capability.
  if(modules.any { it.name == "usuarios" }) names.add(0,"dashboard")
  view.module.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names.map { it.replaceFirstChar(Char::uppercase) })
  val index=names.indexOf(selected).coerceAtLeast(0)
  view.module.setSelection(index)
  view.module.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent: AdapterView<*>?) {}
   override fun onItemSelected(parent: AdapterView<*>?,v: View?,position: Int,id: Long) {
    selected=names[position]; module=modules.find { it.name==selected }
    view.create.visibility=if(module?.permissions?.create==true) View.VISIBLE else View.GONE
    vm.select(selected)
   }
  }
 }
 private fun render(json: String?) {
  val view=recordsView ?: return
  if(json==null) { view.records.adapter=RecordAdapter(emptyList()) {}; return }
  try {
   if(selected=="dashboard") {
    val root=JsonParser.parseString(json).asJsonObject
    val cards=root.entrySet().flatMap { section -> section.value.asJsonObject["cards"].asJsonObject.entrySet().map { card ->
     Record(card.key,card.key.replace('_',' ').replaceFirstChar(Char::uppercase),card.value.asString,JsonObject())
    } }
    view.records.adapter=RecordAdapter(cards) {}; return
   }
   val records=JsonParser.parseString(json).asJsonArray.map { it.asJsonObject.toRecord() }
   view.records.adapter=RecordAdapter(records) { detail(it) }
   if(records.isEmpty()) binding.status.text="No hay registros disponibles."
  } catch(_: Exception) { binding.status.text="No se pudieron interpretar los datos guardados." }
 }
 private fun detail(record: Record) {
  val m=module ?: return
  val dialog=MaterialAlertDialogBuilder(this).setTitle(record.title).setMessage(record.detail).setNegativeButton("Cerrar",null)
  if(m.permissions.update) dialog.setPositiveButton("Editar") { _,_->edit(m,record) }
  if(m.permissions.delete) dialog.setNeutralButton("Eliminar") { _,_->
   MaterialAlertDialogBuilder(this).setTitle("Eliminar registro").setMessage("¿Desea eliminar ${record.title}? Esta operación afecta al sistema central.")
    .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar") { _,_-> vm.delete(m.name,record.id) }.show()
  }
  dialog.show()
 }
 private fun edit(m: Module,record: Record?) {
  vm.prepareForm(m) { relations,services ->
   if(!isFinishing && !isDestroyed) showForm(m,record,relations,services)
  }
 }
 private fun showForm(m: Module,record: Record?,relations: Map<String,List<JsonObject>>,services: List<JsonObject>) {
  val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,16,32,16) }
  val scroll=ScrollView(this).apply { addView(body) }
  val readers=mutableMapOf<String,()->JsonElement?>()
  val validators=mutableListOf<()->Boolean>()
  m.fields.forEach { field ->
   val existing=record?.payload?.get(field.name)?.takeUnless { it.isJsonNull }
   when {
    field.relation!=null -> {
     body.addView(TextView(this).apply { text=field.label + if(field.required) " *" else "" })
     val options=relations[field.relation].orEmpty()
     val spinner=Spinner(this)
     val labels=listOf("Seleccionar…")+options.map { it.toRecord().title }
     spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
     spinner.setSelection(options.indexOfFirst { it["id"]?.asString==existing?.asString }.let { if(it<0) 0 else it+1 })
     body.addView(spinner)
     readers[field.name]={ if(spinner.selectedItemPosition==0) JsonNull.INSTANCE else options[spinner.selectedItemPosition-1]["id"] }
     validators.add { !field.required || spinner.selectedItemPosition>0 }
    }
    field.type=="BooleanField" -> {
     val check=CheckBox(this).apply { text=field.label; isChecked=existing?.asBoolean ?: (field.name in listOf("Estado","EstadoContrato","is_active")) }
     body.addView(check); readers[field.name]={JsonPrimitive(check.isChecked)}
    }
    else -> {
     val layout=TextInputLayout(this).apply { hint=field.label + if(field.required) " *" else "" }
     val input=TextInputEditText(this).apply {
      setText(existing?.asString ?: "")
      inputType=when { field.name=="Contrasena" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
       field.type in listOf("IntegerField","DecimalField") -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
       field.type=="EmailField" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
       else -> InputType.TYPE_CLASS_TEXT }
     }
     if(field.name=="Contrasena") layout.endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE
     if(field.type=="DateField" || field.type=="DateTimeField") layout.helperText=if(field.type=="DateField") "AAAA-MM-DD" else "AAAA-MM-DDTHH:MM:SS-06:00"
     if(field.type=="TimeField") layout.helperText="HH:MM:SS"
     if(field.type in listOf("DateField","DateTimeField","TimeField")) {
      input.isFocusable=false
      input.setOnClickListener {
       val now=java.time.ZonedDateTime.now()
       fun pickTime(date: java.time.LocalDate?) {
        android.app.TimePickerDialog(this,{ _,hour,minute ->
         input.setText(if(date==null) java.time.LocalTime.of(hour,minute).toString() + ":00"
          else java.time.ZonedDateTime.of(date,java.time.LocalTime.of(hour,minute),java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        },now.hour,now.minute,true).show()
       }
       if(field.type=="TimeField") pickTime(null) else {
        val initial=runCatching { java.time.LocalDate.parse(input.text.toString().take(10)) }.getOrDefault(now.toLocalDate())
        android.app.DatePickerDialog(this,{ _,year,month,day ->
         val date=java.time.LocalDate.of(year,month+1,day)
         if(field.type=="DateTimeField") pickTime(date) else input.setText(date.toString())
        },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
       }
      }
     }
     layout.addView(input); body.addView(layout)
     validators.add {
      val value=input.text.toString().trim()
      val required=field.required || (field.name=="Contrasena" && record==null)
      val valid=(!required || value.isNotEmpty()) && (field.max_length==null || value.length<=field.max_length)
      layout.error=if(valid) null else "Revise este campo obligatorio y su longitud"; valid
     }
     readers[field.name]={ input.text.toString().trim().let { value -> if(value.isEmpty() && !field.required) null else JsonPrimitive(value) } }
    }
   }
  }
  val lineReaders=mutableListOf<()->JsonObject>()
  if(m.name=="facturas") {
   body.addView(TextView(this).apply { text="Servicios de la factura"; textSize=18f })
   val lines=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; body.addView(lines)
   fun addLine(old: JsonObject?) {
    val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    val service=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Seleccione servicio")+services.map { it.toRecord().title }) }
    service.setSelection(services.indexOfFirst { it["id"]?.asString==old?.get("ServicioId")?.asString }.let { if(it<0) 0 else it+1 })
    val quantity=TextInputEditText(this).apply { hint="Cantidad"; inputType=InputType.TYPE_CLASS_NUMBER; setText(old?.get("Cantidad")?.asString ?: "1") }
    val price=TextInputEditText(this).apply { hint="Precio de venta (vacío: tarifa del servicio)"; inputType=InputType.TYPE_CLASS_NUMBER; setText(old?.get("PrecioVenta")?.asString ?: "") }
    row.addView(service); row.addView(quantity); row.addView(price)
    val reader: ()->JsonObject = {
     require(service.selectedItemPosition>0) { "Seleccione un servicio para cada línea." }
     val count=quantity.text.toString().toIntOrNull() ?: 0
     require(count>0) { "La cantidad debe ser mayor que cero." }
     JsonObject().apply { add("ServicioId",services[service.selectedItemPosition-1]["id"]); addProperty("Cantidad",count)
      if(price.text.toString().isNotBlank()) addProperty("PrecioVenta",price.text.toString().toInt()) }
    }
    lineReaders.add(reader)
    row.addView(MaterialButton(this).apply { text="Quitar servicio"; setOnClickListener { lines.removeView(row); lineReaders.remove(reader) } })
    lines.addView(row)
   }
   record?.payload?.getAsJsonArray("detalles")?.forEach { addLine(it.asJsonObject) }
   if(record==null) addLine(null)
   body.addView(MaterialButton(this).apply { text="Agregar servicio"; setOnClickListener { addLine(null) } })
  }
  val dialog=MaterialAlertDialogBuilder(this).setTitle(if(record==null) "Crear ${m.name}" else "Editar ${m.name}")
   .setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
  dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
   if(!validators.map { it() }.all { it }) { binding.status.text="Complete los campos y seleccione las relaciones obligatorias."; return@setOnClickListener }
   try {
    val payload=JsonObject(); readers.forEach { (key,read)->read()?.let { payload.add(key,it) } }
    if(m.name=="facturas") {
     require(lineReaders.isNotEmpty()) { "Agregue al menos un servicio." }
     payload.add("lineas",JsonArray().apply { lineReaders.forEach { add(it()) } })
    }
    dialog.getButton(-1).isEnabled=false
    vm.save(m.name,record?.id,payload,{ dialog.dismiss() },{ dialog.getButton(-1).isEnabled=true })
   } catch(e: Exception) { binding.status.text=e.message }
  } }
  dialog.show()
 }
}
