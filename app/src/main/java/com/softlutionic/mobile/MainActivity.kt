package com.softlutionic.mobile
import android.os.Bundle
import android.graphics.Matrix
import android.graphics.RectF
import androidx.core.view.doOnLayout
import android.view.*
import android.widget.*
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
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
import com.softlutionic.mobile.ui.DashboardData
import com.softlutionic.mobile.ui.DashboardRenderer
import com.softlutionic.mobile.ui.FinancialFormat
import com.softlutionic.mobile.ui.clients.ClientsController
import com.softlutionic.mobile.ui.invoices.InvoicesController
import com.softlutionic.mobile.ui.contracttypes.ContractTypesController
import com.softlutionic.mobile.ui.contracts.ContractsController
import com.softlutionic.mobile.ui.users.UsersController
import com.softlutionic.mobile.ui.services.ServicesController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneOffset
import com.softlutionic.mobile.viewmodel.MainViewModel
import com.google.gson.*
import kotlinx.coroutines.launch
class MainActivity: AppCompatActivity() {
 private val noticePermission=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
  if(granted) vm.enableDeviceNotices()
  else vm.consultationNotice.value="Los avisos están desactivados. Puedes permitirlos desde los ajustes de Android."
 }
 fun enableDeviceNotices() {
  if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
   noticePermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
  else if(!com.softlutionic.mobile.notifications.DeviceNotices.allowed(this)) {
   startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))
  } else vm.enableDeviceNotices()
 }
 override fun onNewIntent(intent: android.content.Intent) {
  super.onNewIntent(intent); setIntent(intent)
  if(intent.getBooleanExtra(com.softlutionic.mobile.notifications.DeviceNotices.OPEN,false) && "notificaciones" in availableNames()) {
   intent.removeExtra(com.softlutionic.mobile.notifications.DeviceNotices.OPEN); navigateTo("notificaciones")
  }
 }
 private lateinit var binding: ActivityMainBinding
 private lateinit var vm: MainViewModel
 private var loginView: ViewLoginBinding?=null
 private var recordsView: ViewRecordsBinding?=null
 private var clientsController: ClientsController?=null
 private var contactController: com.softlutionic.mobile.ui.contact.ContactRequestsController?=null
 private var consultationController: com.softlutionic.mobile.ui.consultation.ConsultationController?=null
 private var paymentsController: com.softlutionic.mobile.ui.payments.PaymentsController?=null
 private var invoicesController: InvoicesController?=null
 private var contractTypesController: ContractTypesController?=null
 private var contractsController: ContractsController?=null
 private var usersController: UsersController?=null
 private var servicesController: ServicesController?=null
 private var dashboardView: ViewDashboardBinding?=null
 private var analysisController: com.softlutionic.mobile.ui.analysis.AnalysisController?=null
 private var analysisView: ViewAnalysisBinding?=null
 private var analysisReturnRoute: String?=null
 private var restoredAnalysisFilters: AnalyticsFilters?=null
 private var restoreAnalysis=false
 private val analysisBack=object: androidx.activity.OnBackPressedCallback(false) {
  override fun handleOnBackPressed() { returnFromAnalysis() }
 }
 private fun returnFromAnalysis() {
  val destination=analysisReturnRoute?.takeIf { it in availableNames() && it!="analisis" }
   ?: availableNames().firstOrNull { it!="analisis" }
  if(destination!=null) navigateTo(destination)
  else { analysisBack.isEnabled=false; onBackPressedDispatcher.onBackPressed() }
 }
 private var dashboardRenderer: DashboardRenderer?=null
 private var dashboardData: DashboardData?=null
 private var dashboardTab=0
 private var allowedModules: List<Module> = emptyList()
 private var module: Module?=null
 private var selected=""
 override fun onDestroy() { contactController?.dispose(); consultationController?.dispose(); paymentsController?.dispose(); invoicesController?.dispose(); contractTypesController?.dispose(); contractsController?.dispose(); usersController?.dispose(); clientsController?.dispose(); servicesController?.dispose(); super.onDestroy() }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  restoreAnalysis=savedInstanceState?.getBoolean("analysis_open")==true
  analysisReturnRoute=savedInstanceState?.getString("analysis_return")
  if(restoreAnalysis) restoredAnalysisFilters=runCatching {
   AnalyticsFilters(savedInstanceState?.getString("analysis_start"),savedInstanceState?.getString("analysis_end"))
  }.getOrNull()
  binding=ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
  vm=ViewModelProvider(this)[MainViewModel::class.java]
  onBackPressedDispatcher.addCallback(this,analysisBack)
  binding.status.doOnTextChanged { text, _, _, _ ->
   binding.status.visibility=if(text.isNullOrBlank()) View.GONE else View.VISIBLE
  }
  binding.toolbar.setNavigationIcon(R.drawable.ic_modules)
  binding.toolbar.setNavigationContentDescription(R.string.modules_title)
  binding.toolbar.setNavigationOnClickListener { showModuleSheet() }
  binding.toolbar.menu.add(0,1,0,R.string.refresh).apply {
   setIcon(R.drawable.ic_refresh); setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
   setOnMenuItemClickListener { if(!vm.busy.value) vm.refresh(); true }
  }
  binding.status.setOnClickListener { if(vm.authenticated.value && !vm.busy.value) vm.refresh() }
  lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
   launch { vm.authenticated.collect { if(it) { showRecords(); vm.start() } else { com.softlutionic.mobile.notifications.DeviceNotices.clear(this@MainActivity); showLogin() } } }
   launch { vm.busy.collect { binding.progress.visibility=if(it) View.VISIBLE else View.GONE; loginView?.login?.isEnabled=!it; loginView?.login?.setText(if(it) R.string.login_loading else R.string.login_action); recordsView?.create?.isEnabled=!it; recordsView?.refresh?.isEnabled=!it; clientsController?.busyChanged(); servicesController?.busyChanged(); usersController?.busyChanged(); contractsController?.busyChanged(); contractTypesController?.busyChanged(); invoicesController?.stateChanged(); paymentsController?.stateChanged(); consultationController?.stateChanged(); contactController?.stateChanged(); binding.toolbar.menu.findItem(1)?.isEnabled=!it; updateSyncStatus() } }
   launch { vm.analysisState.collect { analysisController?.render(it) } }
   launch { vm.contactQuery.collect { contactController?.stateChanged() } }
   launch { vm.contactUiState.collect { contactController?.stateChanged() } }
   launch { vm.clientFilters.collect { clientsController?.renderList() } }
   launch { vm.clientDepartments.collect { clientsController?.referencesChanged() } }
   launch { vm.clientDepartmentsLoading.collect { clientsController?.referencesChanged() } }
   launch { vm.clientDepartmentsUnavailable.collect { clientsController?.referencesChanged() } }
   launch { vm.clientSaving.collect { clientsController?.busyChanged() } }
   launch { vm.serviceListState.collect { servicesController?.renderList() } }
   launch { vm.userSaving.collect { usersController?.busyChanged(); contractsController?.busyChanged(); contractTypesController?.busyChanged(); invoicesController?.stateChanged() } }
   launch { vm.userListState.collect { usersController?.renderList() } }
   launch { vm.consultationCatalogs.collect { consultationController?.stateChanged() } }
   launch { vm.consultationFilters.collect { consultationController?.stateChanged() } }
   launch { vm.consultationNotice.collect { consultationController?.stateChanged() } }
   launch { vm.noticeSaving.collect { consultationController?.stateChanged() } }
   launch { vm.noticeLoadError.collect { consultationController?.stateChanged() } }
   launch { vm.unreadNotices.collect { consultationController?.stateChanged() } }
   launch { vm.paymentCatalogs.collect { paymentsController?.stateChanged() } }
   launch { vm.paymentFilters.collect { paymentsController?.stateChanged() } }
   launch { vm.paymentSaving.collect { paymentsController?.stateChanged() } }
   launch { vm.paymentLoading.collect { paymentsController?.stateChanged() } }
   launch { vm.paymentNotice.collect { paymentsController?.stateChanged() } }
   launch { vm.paymentError.collect { paymentsController?.stateChanged() } }
   launch { vm.paymentSaved.collect { paymentsController?.stateChanged() } }
   launch { vm.invoiceSaving.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceClockWarning.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceSubmissionEmission.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceDraft.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceErrors.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceSaved.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceFilters.collect { invoicesController?.renderList() } }
   launch { vm.invoiceCatalogs.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceCatalogsLoading.collect { invoicesController?.stateChanged() } }
   launch { vm.invoiceCatalogsUnavailable.collect { invoicesController?.stateChanged() } }
   launch { vm.contractTypeSaving.collect { contractTypesController?.busyChanged(); invoicesController?.stateChanged() } }
   launch { vm.contractTypeQuery.collect { contractTypesController?.renderList() } }
   launch { vm.contractSaving.collect { contractsController?.busyChanged(); contractTypesController?.busyChanged(); invoicesController?.stateChanged() } }
   launch { vm.contractFilters.collect { contractsController?.renderList() } }
   launch { vm.contractCatalogs.collect { contractsController?.renderList() } }
   launch { vm.contractCatalogsLoading.collect { contractsController?.renderList() } }
   launch { vm.contractCatalogsUnavailable.collect { contractsController?.renderList() } }
   launch { vm.serviceSaving.collect { servicesController?.busyChanged() } }
   launch { vm.message.collect { updateSyncStatus() } }
   launch { vm.dashboardAllowed.collect { updateModules(vm.modules.value) } }
   launch { vm.currentIdentity.collect { updateModules(vm.modules.value) } }
   launch { vm.modules.collect { updateModules(it) } }
   launch { vm.content.collect { render(it) } }
   launch { vm.recordCatalogs.collect { if(recordsView!=null) render(vm.content.value) } }
  } }
 }
 private fun showLogin() {
  contactController?.dispose(); contactController=null
  consultationController?.dispose(); consultationController=null
  paymentsController?.dispose(); paymentsController=null
  invoicesController?.dispose(); invoicesController=null
  contractTypesController?.dispose(); contractTypesController=null
  contractsController?.dispose(); contractsController=null
  usersController?.dispose(); usersController=null
  servicesController?.dispose(); servicesController=null
  clientsController?.dispose(); clientsController=null
  recordsView=null; dashboardView=null; analysisController=null; analysisView=null; analysisBack.isEnabled=false; dashboardRenderer=null; dashboardData=null; selected=""; binding.toolbar.visibility=View.GONE
  val login=ViewLoginBinding.inflate(layoutInflater,binding.content,false)
  // Frame the original symbol without changing or resampling the brand asset.
  login.loginLogo.doOnLayout { image ->
   val drawable=login.loginLogo.drawable
   val w=drawable.intrinsicWidth.toFloat()
   val h=drawable.intrinsicHeight.toFloat()
   val symbol=RectF(w*0.20f,h*0.275f,w*0.38f,h*0.73f)
   login.loginLogo.imageMatrix=Matrix().apply {
    setRectToRect(symbol,RectF(0f,0f,image.width.toFloat(),image.height.toFloat()),Matrix.ScaleToFit.CENTER)
   }
  }
  listOf(login.username to login.usernameLayout, login.password to login.passwordLayout).forEach { (input, container) ->
   input.setOnFocusChangeListener { _, focused ->
    container.animate().translationZ(if(focused) 4f * resources.displayMetrics.density else 0f).setDuration(160).start()
   }
  }
  loginView=login
  login.login.isEnabled=!vm.busy.value
  login.login.setText(if(vm.busy.value) R.string.login_loading else R.string.login_action)
  login.password.setOnEditorActionListener { _, action, _ ->
   if(action==EditorInfo.IME_ACTION_DONE) {
    if(login.login.isEnabled) login.login.performClick()
    true
   } else false
  }
  binding.content.removeAllViews(); binding.content.addView(login.root)
  login.login.setOnClickListener {
   val user=login.username.text.toString().trim(); val password=login.password.text.toString()
   login.usernameLayout.error=if(user.isBlank()) getString(R.string.login_user_required) else null
   login.passwordLayout.error=if(password.isBlank()) getString(R.string.login_password_required) else null
   if(user.isNotBlank() && password.isNotBlank() && !vm.busy.value) vm.login(user,password)
  }
 }
 private fun showRecords() {
  loginView=null; binding.toolbar.visibility=View.VISIBLE
  updateModules(vm.modules.value)
  updateSyncStatus()
 }
 override fun onSaveInstanceState(outState: Bundle) {
  outState.putBoolean("analysis_open",selected=="analisis")
  outState.putString("analysis_return",analysisReturnRoute)
  outState.putString("analysis_start",vm.analysisState.value.filters.start)
  outState.putString("analysis_end",vm.analysisState.value.filters.end)
  super.onSaveInstanceState(outState)
 }
 private fun moduleLabel(name: String): String {
  val labels=mapOf(ContactRequestDto.MODULE to R.string.module_contact,"analisis" to R.string.module_analysis,"dashboard" to R.string.module_dashboard,"clientes" to R.string.module_clients,
   "departamentos" to R.string.module_departments,"servicios" to R.string.module_services,"usuarios" to R.string.module_users,
   "contratos" to R.string.module_contracts,"tipos-contrato" to R.string.module_contract_types,"tipos_contrato" to R.string.module_contract_types,
   "facturas" to R.string.module_invoices,"estados-factura" to R.string.module_invoice_states,"estados_factura" to R.string.module_invoice_states,
   "pagos" to R.string.module_payments,"moras" to R.string.module_late,"notificaciones" to R.string.module_notifications,"auditoria" to R.string.module_audit)
  return labels[name]?.let { getString(it) } ?: name.replace('_',' ').replace('-',' ').replaceFirstChar(Char::uppercase)
 }
 private fun availableNames(): List<String> = (if(vm.dashboardAllowed.value) listOf("dashboard") else emptyList())+allowedModules.map { it.name }+listOf("analisis")
 private fun updateModules(modules: List<Module>) {
  // Presentation only: master catalogs stay in vm.modules/schema for internal relations.
  // availableNames() also guards navigateTo(), so Departamentos has no mobile CRUD entry.
  allowedModules=modules.map { vm.effectiveModule(it) }.filter { it.permissions.read && com.softlutionic.mobile.ui.payments.MobileNavigation.visible(it.name) && com.softlutionic.mobile.ui.consultation.ConsultationPresentation.canRead(vm.currentIdentity.value,it) }
  module=allowedModules.find { it.name==selected }
  recordsView?.create?.visibility=if(module?.permissions?.create==true) View.VISIBLE else View.GONE
  if(!vm.authenticated.value || loginView!=null) return
  // Wait for the existing schema before choosing the default remote module.
  if(modules.isEmpty() && selected.isBlank() && !restoreAnalysis) return
  val names=availableNames()
  if(restoreAnalysis && "analisis" in names) {
   restoreAnalysis=false
   val previous=analysisReturnRoute
   navigateTo("analisis")
   analysisReturnRoute=previous
   return
  }
  if(intent.getBooleanExtra(com.softlutionic.mobile.notifications.DeviceNotices.OPEN,false) && "notificaciones" in names) {
   intent.removeExtra(com.softlutionic.mobile.notifications.DeviceNotices.OPEN); navigateTo("notificaciones"); return
  }
  if(names.isEmpty()) {
   contactController?.dispose(); contactController=null
  consultationController?.dispose(); consultationController=null
  paymentsController?.dispose(); paymentsController=null
  invoicesController?.dispose(); invoicesController=null
  contractTypesController?.dispose(); contractTypesController=null
  contractsController?.dispose(); contractsController=null
  usersController?.dispose(); usersController=null
  servicesController?.dispose(); servicesController=null
   clientsController?.dispose(); clientsController=null
   binding.content.removeAllViews(); recordsView=null; dashboardView=null
   binding.status.setText(R.string.no_modules); return
  }
  clientsController?.busyChanged()
  servicesController?.busyChanged()
  usersController?.busyChanged(); contractsController?.busyChanged(); contractTypesController?.busyChanged(); invoicesController?.stateChanged(); paymentsController?.stateChanged(); consultationController?.stateChanged(); contactController?.stateChanged()
  if(selected !in names || (analysisView==null && recordsView==null && dashboardView==null && clientsController==null && servicesController==null && usersController==null && contractsController==null && contractTypesController==null && invoicesController==null && paymentsController==null && consultationController==null && contactController==null)) navigateTo(names.firstOrNull { it==selected } ?: names.first())
 }
 private fun navigateTo(name: String) {
  if(name !in availableNames()) return
  if(name=="analisis" && selected!="analisis") analysisReturnRoute=selected
  selected=name; module=allowedModules.find { it.name==name }
  analysisBack.isEnabled=name=="analisis"
  binding.toolbar.menu.findItem(1)?.isVisible=name!="analisis"
  contactController?.dispose(); contactController=null
  consultationController?.dispose(); consultationController=null
  paymentsController?.dispose(); paymentsController=null
  invoicesController?.dispose(); invoicesController=null
  contractTypesController?.dispose(); contractTypesController=null
  contractsController?.dispose(); contractsController=null
  usersController?.dispose(); usersController=null
  servicesController?.dispose(); servicesController=null
  clientsController?.dispose(); clientsController=null
  recordsView=null; dashboardView=null; analysisController=null; analysisView=null; dashboardRenderer=null; dashboardData=null
  binding.content.removeAllViews()
  if(name=="analisis") {
   val view=ViewAnalysisBinding.inflate(layoutInflater,binding.content,false)
   analysisView=view
   analysisController=com.softlutionic.mobile.ui.analysis.AnalysisController(this,view,{ vm.loadAnalysis(it) },::returnFromAnalysis)
   analysisController?.render(vm.analysisState.value)
   val restored=restoredAnalysisFilters
   restoredAnalysisFilters=null
   if(restored!=null && !vm.analysisState.value.loaded && !vm.analysisState.value.loading) vm.loadAnalysis(restored)
   else vm.openAnalysis()
   binding.content.addView(view.root)
  } else if(name=="dashboard") {
   val view=ViewDashboardBinding.inflate(layoutInflater,binding.content,false)
   dashboardView=view
   dashboardRenderer=DashboardRenderer(view,if(allowedModules.any { it.name=="facturas" }) ({ navigateTo("facturas") }) else null)
   view.tabs.addTab(view.tabs.newTab().setText(R.string.billing_tab))
   view.tabs.addTab(view.tabs.newTab().setText(R.string.commercial_tab))
   view.tabs.getTabAt(dashboardTab)?.select()
   view.tabs.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: TabLayout.Tab) {
     dashboardTab=tab.position; dashboardRenderer?.render(dashboardData,dashboardTab==1)
     view.panels.alpha=.75f; view.panels.animate().alpha(1f).setDuration(180).start()
    }
    override fun onTabUnselected(tab: TabLayout.Tab) {}
    override fun onTabReselected(tab: TabLayout.Tab) {}
   })
   view.filter.setOnClickListener { showPeriodSheet() }
   updatePeriodLabel()
   dashboardRenderer?.render(null,dashboardTab==1)
   binding.content.addView(view.root)
  } else if(name==ContactRequestDto.MODULE) {
   val view=ViewContactRequestsBinding.inflate(layoutInflater,binding.content,false)
   contactController=com.softlutionic.mobile.ui.contact.ContactRequestsController(this,view,vm)
   binding.content.addView(view.root)
  } else if(name in com.softlutionic.mobile.ui.consultation.ConsultationPresentation.routes) {
   consultationController=com.softlutionic.mobile.ui.consultation.ConsultationController(this,requireNotNull(module),vm)
   binding.content.addView(consultationController!!.root)
  } else if(name=="pagos") {
   paymentsController=com.softlutionic.mobile.ui.payments.PaymentsController(this,requireNotNull(module),vm)
   binding.content.addView(paymentsController!!.root)
  } else if(name=="facturas") {
   invoicesController=InvoicesController(this,requireNotNull(module),vm)
   binding.content.addView(invoicesController!!.root)
  } else if(name=="tipos-contrato") {
   contractTypesController=ContractTypesController(this,requireNotNull(module),vm)
   binding.content.addView(contractTypesController!!.root)
  } else if(name=="contratos") {
   contractsController=ContractsController(this,requireNotNull(module),vm)
   binding.content.addView(contractsController!!.root)
  } else if(name=="usuarios") {
   usersController=UsersController(this,requireNotNull(module),vm)
   binding.content.addView(usersController!!.root)
  } else if(name=="servicios") {
   // Specialized service presentation; the shared CRUD and invoice catalog remain unchanged.
   val view=ViewServicesBinding.inflate(layoutInflater,binding.content,false)
   servicesController=ServicesController(view,requireNotNull(module),vm)
   binding.content.addView(view.root)
  } else if(name=="clientes") {
   // Client-specific views reuse the same ViewModel, Repository and API operations.
   val view=ViewClientsBinding.inflate(layoutInflater,binding.content,false)
   clientsController=ClientsController(view,requireNotNull(module),vm)
   binding.content.addView(view.root)
  } else {
   module?.let { vm.loadRecordCatalogs(it) }
   val view=ViewRecordsBinding.inflate(layoutInflater,binding.content,false); recordsView=view
   view.moduleTitle.text=moduleLabel(name)
   binding.content.addView(view.root)
   view.records.layoutManager=LinearLayoutManager(this)
   view.refresh.setOnClickListener { if(!vm.busy.value) vm.refresh() }
   view.create.visibility=if(module?.permissions?.create==true) View.VISIBLE else View.GONE
   view.create.setOnClickListener { module?.let { edit(it,null) } }
  }
  vm.select(name)
  updateSyncStatus()
 }
 private fun showModuleSheet() {
  val sheet=BottomSheetDialog(this,R.style.ThemeOverlay_Softlutionic_Sheet)
  val view=BottomSheetModulesBinding.inflate(layoutInflater)
  val icons=mapOf(ContactRequestDto.MODULE to R.drawable.ic_contact,"analisis" to R.drawable.ic_analysis,"dashboard" to R.drawable.ic_dashboard, "facturas" to R.drawable.ic_invoice,
   "clientes" to R.drawable.login_user,"usuarios" to R.drawable.login_user,"pagos" to R.drawable.ic_paid,
   "moras" to R.drawable.ic_pending,"servicios" to R.drawable.ic_services,"contratos" to R.drawable.ic_invoice,
   "auditoria" to R.drawable.login_lock)
  availableNames().forEach { name ->
   view.modules.addView(MaterialButton(view.root.context).apply {
    text=moduleLabel(name); setIconResource(icons[name] ?: R.drawable.ic_dashboard); iconTint=android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context,R.color.login_cyan))
    minHeight=(56*resources.displayMetrics.density).toInt(); gravity=Gravity.START or Gravity.CENTER_VERTICAL
    isSelected=name==selected
    setTextColor(ContextCompat.getColor(context,if(isSelected) R.color.login_cyan else R.color.login_text))
    backgroundTintList=android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context,if(isSelected) R.color.login_surface else R.color.login_bg))
    setOnClickListener { sheet.dismiss(); if(name!=selected) navigateTo(name) }
   })
  }
  view.logout.setOnClickListener { sheet.dismiss(); vm.logout() }
  sheet.setContentView(view.root)
  sheet.behavior.maxHeight=(resources.displayMetrics.heightPixels*.85f).toInt()
  sheet.show()
 }
 private fun updatePeriodLabel() {
  val range=vm.period.value
  dashboardView?.periodScope?.visibility=View.VISIBLE
  dashboardView?.periodScope?.text="Período: "+(range?.let { FinancialFormat.date(it.first)+" → "+FinancialFormat.date(it.second) } ?: "Todo el historial")+"\n"+getString(R.string.filter_hint)
  dashboardView?.filter?.text=if(range==null) getString(R.string.filter_period) else getString(R.string.period_value,FinancialFormat.date(range.first),FinancialFormat.date(range.second))
 }
 private fun showPeriodSheet() {
  val sheet=BottomSheetDialog(this,R.style.ThemeOverlay_Softlutionic_Sheet)
  val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,24,32,32); setBackgroundColor(ContextCompat.getColor(context,R.color.login_bg)) }
  body.addView(TextView(this).apply { setText(R.string.filter_period); textSize=22f; setTextColor(ContextCompat.getColor(context,R.color.login_text)) })
  body.addView(TextView(this).apply { setText(R.string.filter_hint); textSize=14f; setPadding(0,16,0,16); setTextColor(ContextCompat.getColor(context,R.color.login_muted)) })
  // Dashboard: quick ranges reuse the existing start/end API parameters.
  val today=java.time.LocalDate.now()
  val week=today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
  val presets=listOf("Hoy" to (today to today), "Ayer" to (today.minusDays(1) to today.minusDays(1)),
   "Anteayer" to (today.minusDays(2) to today.minusDays(2)), "Esta semana" to (week to today),
   "Este mes" to (today.withDayOfMonth(1) to today))
  presets.forEach { (label,range) ->
   body.addView(MaterialButton(this).apply {
    text=label; isAllCaps=false
    setOnClickListener {
     sheet.dismiss(); dashboardData=null; dashboardRenderer?.render(null,dashboardTab==1)
     vm.filterPeriod(range.first.toString() to range.second.toString()); updatePeriodLabel()
    }
   })
  }
  val date=MaterialButton(this).apply {
   text=vm.period.value?.let { getString(R.string.period_value,FinancialFormat.date(it.first),FinancialFormat.date(it.second)) } ?: "Rango personalizado"
  }
  body.addView(date)
  date.setOnClickListener {
   val builder=MaterialDatePicker.Builder.dateRangePicker().setTheme(R.style.ThemeOverlay_Softlutionic_Calendar).setTitleText(R.string.filter_period).setPositiveButtonText(R.string.apply).setNegativeButtonText(R.string.cancel)
   vm.period.value?.let { range -> builder.setSelection(androidx.core.util.Pair(java.time.LocalDate.parse(range.first).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),java.time.LocalDate.parse(range.second).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())) }
   val picker=builder.build()
   picker.addOnPositiveButtonClickListener { range ->
    fun iso(time: Long)=Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate().toString()
    sheet.dismiss(); dashboardData=null; dashboardRenderer?.render(null,dashboardTab==1)
    vm.filterPeriod(iso(range.first) to iso(range.second)); updatePeriodLabel()
   }
   picker.show(supportFragmentManager,"dashboard_period")
  }
  body.addView(MaterialButton(this).apply { setText(R.string.clear_filter); setOnClickListener {
   sheet.dismiss(); dashboardData=null; dashboardRenderer?.render(null,dashboardTab==1); vm.filterPeriod(null); updatePeriodLabel()
  } })
  sheet.setContentView(ScrollView(this).apply { addView(body) }); sheet.show()
 }
 private fun updateSyncStatus() {
  if(selected=="analisis") { binding.status.text=""; binding.progress.visibility=View.GONE; return }
  val message=vm.message.value
  if(!vm.authenticated.value) {
   binding.status.setTextColor(ContextCompat.getColor(this,R.color.login_text))
   binding.status.isClickable=false; binding.status.isFocusable=false
   binding.status.text=message; return
  }
  val offline=message.contains("Sin conex",true) || message.contains("espera agotado",true)
  val error=message.isNotBlank() && message!="Actualizado" && !offline
  val color=when { vm.busy.value -> R.color.login_cyan; offline -> R.color.dashboard_amber; error -> R.color.login_error; else -> R.color.dashboard_green }
  binding.status.setTextColor(ContextCompat.getColor(this,color))
  binding.status.compoundDrawableTintList=android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this,color))
  binding.status.isClickable=(offline || error) && !vm.busy.value
  binding.status.isFocusable=binding.status.isClickable
  binding.status.minimumHeight=if(binding.status.isClickable) (48*resources.displayMetrics.density).toInt() else 0
  binding.status.text=when {
   vm.busy.value -> "↻  "+getString(R.string.sync_busy)
   offline -> "ⓘ  "+getString(R.string.sync_offline)
   error -> "!  "+getString(R.string.sync_error,message)+" \u00b7 "+getString(R.string.retry)
   message=="Actualizado" -> "✓  "+getString(R.string.sync_ok)
   else -> ""
  }
 }
 private fun render(json: String?) {
  if(selected=="analisis") return
  if(selected==ContactRequestDto.MODULE) { contactController?.submit(json); return }
  if(selected in com.softlutionic.mobile.ui.consultation.ConsultationPresentation.routes) { consultationController?.submit(json); return }
  if(selected=="pagos") { paymentsController?.submit(json); return }
  if(selected=="facturas") { invoicesController?.submit(json); return }
  if(selected=="tipos-contrato") { contractTypesController?.submit(json); return }
  if(selected=="contratos") { contractsController?.submit(json); return }
  if(selected=="usuarios") {
   try { usersController?.submit(json) } catch(_: Exception) { binding.status.setText(R.string.data_error) }
   return
  }
  if(selected=="servicios") {
   try { servicesController?.submit(json) } catch(_: Exception) { binding.status.setText(R.string.data_error) }
   return
  }
  if(selected=="clientes") {
   try { clientsController?.submit(json) } catch(_: Exception) { binding.status.setText(R.string.data_error) }
   return
  }
  if(selected=="dashboard") {
   try {
    dashboardData=json?.let { DashboardData.parse(it) }
    dashboardRenderer?.render(dashboardData,dashboardTab==1)
   } catch(_: Exception) { binding.status.setText(R.string.data_error) }
   return
  }
  val view=recordsView ?: return
  if(json==null) { view.records.adapter=RecordAdapter(emptyList()) {}; return }
  try {
   val parsed=JsonParser.parseString(json)
   if(!parsed.isJsonArray) return
   val relations=module?.fields?.mapNotNull { field -> field.relation?.let { field.name to it } }?.toMap().orEmpty()
   val records=parsed.asJsonArray.map { it.asJsonObject.toRecord(vm.recordCatalogs.value,relations) }
   view.records.adapter=RecordAdapter(records) { detail(it) }
   if(records.isEmpty()) binding.status.text=getString(R.string.no_records)
  } catch(_: Exception) { binding.status.setText(R.string.data_error) }
 }
 private fun detail(record: Record) {
  val m=module?.let { vm.effectiveModule(it) } ?: return
  val dialog=MaterialAlertDialogBuilder(ContextThemeWrapper(this,R.style.ThemeOverlay_Softlutionic_Login))
   .setBackground(android.graphics.drawable.GradientDrawable().apply {
    setColor(ContextCompat.getColor(this@MainActivity,R.color.login_surface)); cornerRadius=20*resources.displayMetrics.density
   }).setTitle(record.title).setMessage(record.detail).setNegativeButton("Cerrar",null)
  if(m.permissions.update) dialog.setPositiveButton("Editar") { _,_->edit(m,record) }
  if(m.permissions.delete) dialog.setNeutralButton("Eliminar") { _,_->
   MaterialAlertDialogBuilder(this).setTitle("Eliminar registro").setMessage("¿Desea eliminar ${record.title}? Esta operación afecta al sistema central.")
    .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar") { _,_-> vm.delete(m.name,record.id) }.show()
  }
  dialog.show()
 }
 private fun edit(m: Module,record: Record?) {
  val permissions=vm.effectiveModule(m).permissions
  if(!(if(record==null) permissions.create else permissions.update)) {
   vm.message.value=com.softlutionic.mobile.domain.auth.AuthorizationDenied(m.name).message.orEmpty()
   return
  }
  vm.prepareForm(m) { relations,services ->
   if(!isFinishing && !isDestroyed) showForm(m,record,relations,services)
  }
 }
 private fun showForm(m: Module,record: Record?,relations: Map<String,List<JsonObject>>,services: List<JsonObject>) {
  val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,16,32,16) }
  val scroll=ScrollView(this).apply { addView(body) }
  val readers=mutableMapOf<String,()->JsonElement?>()
  val validators=mutableListOf<()->Boolean>()
  m.fields.filter { !it.read_only && (!com.softlutionic.mobile.ui.RecordPresentation.isTechnicalIdField(it.name) || it.relation!=null) }.forEach { field ->
   val existing=record?.payload?.get(field.name)?.takeUnless { it.isJsonNull }
   when {
    field.relation!=null -> {
     body.addView(TextView(this).apply { text=com.softlutionic.mobile.ui.RecordPresentation.label(field.name) + if(field.required) " *" else "" })
     val options=relations[field.relation].orEmpty()
     val spinner=Spinner(this)
     val labels=listOf("Seleccionar…")+options.map { it.toRecord(vm.recordCatalogs.value+relations).title }
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
