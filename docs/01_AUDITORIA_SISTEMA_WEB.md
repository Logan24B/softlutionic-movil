# Auditoría del sistema web Softlutionic

Fecha: 22 de octubre de 2026. Rama verificada: `mobile-development`; árbol inicialmente limpio.

## Estructura y aplicaciones

Django 4.2 + DRF; `SoftFactur/settings.py`, `SoftFactur/database_config.py`, URLs raíz y `apps/urls.py`.
`INSTALLED_APPS`: admin, auth, contenttypes, sessions, messages, staticfiles, rest_framework,
dashboard, usuarios, clientes, departamentos, servicios, contratos, factura, pagos, mora,
notificaciones y auditoria. `colores/models.py` está vacío y no figura como aplicación instalada.
No se confirmó un modelo Producto: `productos.html`/`productos.js` son archivos del frontend,
no justifican inventar un módulo móvil de inventario.

## Autenticación y permisos comprobados

`usuarios.user` hereda AbstractUser. `Rol=True` significa administrador; el modelo sincroniza
is_staff y nombres. El login web acepta usuario o correo y mantiene sesión Django/CSRF.
DRF global conserva SessionAuthentication y BasicAuthentication con IsAuthenticated.
Dashboard/reportes exigen Rol; escritura de usuarios exige Rol; frontend oculta usuarios,
reportes y dashboard a empleados. Los demás ViewSets permiten operaciones a autenticados.
La API móvil restringe además usuarios/auditoría a administradores, mora a consulta y evita
DELETE de factura/pago por su impacto contable. Es una política conservadora documentada;
no modifica los permisos de las rutas web existentes.

## Reglas de negocio

Clientes: teléfono nicaragüense normalizado +505, cédula/correo/teléfono únicos; departamentos
protegidos. Servicios: precio entero no negativo y nombre único. Contratos: fin no anterior
al inicio, cliente obligatorio y tipo opcional. Factura: código único, usuario/contrato, detalles
con cantidad positiva, precios por defecto del servicio, total calculado en el modelo.
`factura/services.py`: contrato «Servicio mensual» vence a 30 días; otros el mismo día a las
23:59; mora diaria 0.07%, exclusivamente mensual; un pago confirmado basta para marcar pagada
(regla existente, no se ha reinterpretado como suma de pagos). Mora OneToOne con factura.
GET de facturas/moras sincroniza estados y puede escribir: no se consultó producción.
El frontend guarda encabezado y detalles en solicitudes separadas; la API móvil mejora esa
operación con transacción atómica y `lineas`. No se expone CRUD móvil independiente de detalles.

## Dashboard e identidad visual

Se reutilizan las respuestas web facturacion y clientes-servicios: total facturado, pagado,
saldo, mora, clientes, contratos, ticket promedio e ingresos por servicio; no se inventan KPIs.
El dashboard existente agrega pagos según su implementación actual, que se conserva.
Paleta de styles.css: morado #6d5dfc, fondo #f5f7fb, lateral #121827, turquesa #14b8a6.
Se copia el logo real `frontend/Softlutionic/assets/logo.png`. Formularios Android nativos;
no HTML ni WebView. Reportes gerenciales permanecen en web; no hay exportador móvil aún.

## Modelos y campos inspeccionados

### Cliente — `apps/catalogos/clientes/models.py`

- `DepartamentoId: models.ForeignKey('departamentos.Departamentos', on_delete=models.PROTECT, related_name='clientes', db_column='DepartamentoId')`
- `Nombre: models.CharField(max_length=50)`
- `Apellido: models.CharField(max_length=50)`
- `Cedula: models.CharField(max_length=20, null=True, blank=True, validators=[cedula_validator])`
- `Telefono: models.CharField(max_length=25, unique=True, validators=[telefono_validator])`
- `Direccion: models.CharField(max_length=100)`
- `FechaRegistro: models.DateField(auto_now_add=True)`
- `HoraRegistro: models.TimeField(auto_now_add=True)`
- `Correo: models.EmailField(max_length=50, unique=True)`
- `Estado: models.BooleanField(default=True)`

### Departamentos — `apps/catalogos/departamentos/models.py`

- `Nombre: models.CharField(max_length=50, unique=True)`

### Servicio — `apps/catalogos/servicios/models.py`

- `Nombre_Servicio: models.CharField(max_length=50, unique=True)`
- `Descripcion: models.CharField(max_length=200)`
- `Precio: models.IntegerField()`
- `Duracion: models.CharField(max_length=50)`

### user — `apps/seguridad/usuarios/models.py`

- `PrimerNombre: models.CharField(max_length=50)`
- `SegundoNombre: models.CharField(max_length=50)`
- `FechaRegistro: models.DateField(auto_now_add=True)`
- `HoraRegistro: models.TimeField(auto_now_add=True)`
- `Rol: models.BooleanField(default=False)`

### LogAuditoria — `apps/transaccion/auditoria/models.py`

- `IdLog: models.BigAutoField(primary_key=True)`
- `Tabla: models.CharField(max_length=128)`
- `Accion: models.CharField(max_length=20)`
- `ClavePrincipal: models.CharField(max_length=128, null=True, blank=True)`
- `Usuario: models.CharField(max_length=128, default='')`
- `Host: models.CharField(max_length=128, default='')`
- `Fecha: models.DateTimeField()`
- `Detalle: models.CharField(max_length=4000, null=True, blank=True)`

### TipoContrato — `apps/transaccion/contratos/models.py`

- `Nombre: models.CharField(max_length=80, unique=True)`
- `Descripcion: models.CharField(max_length=250, blank=True)`

### Contrato — `apps/transaccion/contratos/models.py`

- `ClienteId: models.ForeignKey('clientes.Cliente', on_delete=models.PROTECT, related_name='contratos', db_column='ClienteId')`
- `TipoContratoId: models.ForeignKey(TipoContrato, on_delete=models.PROTECT, related_name='contratos', db_column='TipoContratoId', null=True, blank=True)`
- `Fecha_Inc: models.DateField()`
- `Fecha_Fin: models.DateField()`
- `EstadoContrato: models.BooleanField(default=True)`
- `Descripcion: models.CharField(max_length=250)`

### EstadoFactura — `apps/transaccion/factura/models.py`

- `estado: models.CharField(max_length=50, unique=True)`

### Factura — `apps/transaccion/factura/models.py`

- `UsuarioId: models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='facturas', db_column='UsuarioId')`
- `ContratoId: models.ForeignKey('contratos.Contrato', on_delete=models.PROTECT, related_name='facturas', db_column='ContratoId')`
- `Fecha_Emision: models.DateField()`
- `Hora_Emision: models.TimeField()`
- `Fecha_Vencimiento: models.DateField()`
- `Hora_Vencimiento: models.TimeField()`
- `Monto_Total: models.DecimalField(max_digits=18, decimal_places=2, default=0)`
- `CodigoFact: models.IntegerField(unique=True)`
- `EstadoId: models.ForeignKey(EstadoFactura, on_delete=models.PROTECT, related_name='facturas', db_column='EstadoId', default=1)`

### DetalleFactura — `apps/transaccion/factura/models.py`

- `FacturaId: models.ForeignKey(Factura, on_delete=models.CASCADE, related_name='detalles', db_column='FacturaId')`
- `ServicioId: models.ForeignKey('servicios.Servicio', on_delete=models.PROTECT, related_name='detalles_factura', db_column='ServicioId')`
- `PrecioVenta: models.IntegerField()`
- `Cantidad: models.PositiveIntegerField(default=1)`

### Mora — `apps/transaccion/mora/models.py`

- `Fecha_Inicio: models.DateField()`
- `Hora_Inicio: models.TimeField()`
- `Fecha_Final: models.DateField()`
- `Hora_Final: models.TimeField()`
- `Monto_Mora: models.DecimalField(max_digits=18, decimal_places=2)`
- `EstadoMora: models.BooleanField(default=False)`
- `FacturaId: models.OneToOneField('factura.Factura', on_delete=models.PROTECT, related_name='mora', db_column='FacturaId')`
- `DescripcionMora: models.CharField(max_length=500)`

### Notificacion — `apps/transaccion/notificaciones/models.py`

- `ClienteId: models.ForeignKey('clientes.Cliente', on_delete=models.PROTECT, related_name='notificaciones', db_column='ClienteId')`
- `Fecha_Envio: models.DateTimeField()`
- `Mensaje: models.CharField(max_length=250)`
- `EstadoNotificacion: models.BooleanField(default=False)`

### Pago — `apps/transaccion/pagos/models.py`

- `Fecha_Pago: models.DateTimeField()`
- `Monto_Pagado: models.DecimalField(max_digits=18, decimal_places=2)`
- `EstadoPago: models.BooleanField(default=False)`
- `MetodoPago: models.BooleanField(default=False)`
- `FacturaId: models.ForeignKey('factura.Factura', on_delete=models.PROTECT, related_name='pagos', db_column='FacturaId')`
- `NumeroRecibo: models.IntegerField(unique=True)`

## Relaciones

- `Cliente.DepartamentoId`: `models.ForeignKey('departamentos.Departamentos', on_delete=models.PROTECT, related_name='clientes', db_column='DepartamentoId')`
- `Contrato.ClienteId`: `models.ForeignKey('clientes.Cliente', on_delete=models.PROTECT, related_name='contratos', db_column='ClienteId')`
- `Contrato.TipoContratoId`: `models.ForeignKey(TipoContrato, on_delete=models.PROTECT, related_name='contratos', db_column='TipoContratoId', null=True, blank=True)`
- `Factura.UsuarioId`: `models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='facturas', db_column='UsuarioId')`
- `Factura.ContratoId`: `models.ForeignKey('contratos.Contrato', on_delete=models.PROTECT, related_name='facturas', db_column='ContratoId')`
- `Factura.EstadoId`: `models.ForeignKey(EstadoFactura, on_delete=models.PROTECT, related_name='facturas', db_column='EstadoId', default=1)`
- `DetalleFactura.FacturaId`: `models.ForeignKey(Factura, on_delete=models.CASCADE, related_name='detalles', db_column='FacturaId')`
- `DetalleFactura.ServicioId`: `models.ForeignKey('servicios.Servicio', on_delete=models.PROTECT, related_name='detalles_factura', db_column='ServicioId')`
- `Mora.FacturaId`: `models.OneToOneField('factura.Factura', on_delete=models.PROTECT, related_name='mora', db_column='FacturaId')`
- `Notificacion.ClienteId`: `models.ForeignKey('clientes.Cliente', on_delete=models.PROTECT, related_name='notificaciones', db_column='ClienteId')`
- `Pago.FacturaId`: `models.ForeignKey('factura.Factura', on_delete=models.PROTECT, related_name='pagos', db_column='FacturaId')`

No se encontraron relaciones ManyToMany empresariales; AbstractUser conserva grupos y permisos
ManyToMany propios de Django. PROTECT impide borrar referencias; detalles usan CASCADE.

## APIs y rutas existentes

Prefijo raíz `/api/`. Se conservan todos los routers y serializers originales.

### `apps/catalogos/clientes/urls.py`

```python
# clientes/urls.py
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .api import ClienteViewSet

# El router se encarga de generar las rutas típicas de un CRUD
router = DefaultRouter()
# 'clientes' será el prefijo de la URL, y 'ClienteViewSet' la lógica
router.register(r'clientes', ClienteViewSet, basename='cliente')

app_name = 'clientes'

urlpatterns = [
    # Incluimos todas las rutas generadas por el router
    path('', include(router.urls)),
]
```

### `apps/catalogos/departamentos/urls.py`

```python
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .api import DepartamentosViewSet

router = DefaultRouter()
router.register(r'departamentos', DepartamentosViewSet, basename='departamentos')

app_name = 'departamentos'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/catalogos/servicios/urls.py`

```python
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .api import ServicioViewSet

router = DefaultRouter()
router.register(r'servicios', ServicioViewSet, basename='servicios')

app_name = 'servicios'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/dashboard/urls.py`

```python
from django.urls import path
from .reports import ManagementReportsAPIView

from .api import (
    DashboardClientesServiciosAPIView,
    DashboardFacturacionAPIView,
    DashboardVersionAPIView,
)


urlpatterns = [
    path("reportes/", ManagementReportsAPIView.as_view(), name="management_reports"),
    path("dashboard/version/", DashboardVersionAPIView.as_view(), name="dashboard_version"),
    path("dashboard/facturacion/", DashboardFacturacionAPIView.as_view(), name="dashboard_facturacion"),
    path(
        "dashboard/clientes-servicios/",
        DashboardClientesServiciosAPIView.as_view(),
        name="dashboard_clientes_servicios",
    ),
]
```

### `apps/seguridad/usuarios/urls.py`

```python
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .api import UserViewSet
from .auth_api import (
    LoginAPIView,
    LogoutAPIView,
    PasswordResetConfirmAPIView,
    PasswordResetRequestAPIView,
    SessionAPIView,
)

router = DefaultRouter()
router.register(r'usuarios', UserViewSet, basename='usuarios')

app_name = 'usuarios'

urlpatterns = [
    path('auth/login/', LoginAPIView.as_view(), name='auth_login'),
    path('auth/session/', SessionAPIView.as_view(), name='auth_session'),
    path('auth/logout/', LogoutAPIView.as_view(), name='auth_logout'),
    path('auth/password-reset/', PasswordResetRequestAPIView.as_view(), name='password_reset'),
    path('auth/password-reset/confirm/', PasswordResetConfirmAPIView.as_view(), name='password_reset_confirm'),
    path('', include(router.urls)),
]
```

### `apps/transaccion/auditoria/urls.py`

```python
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .api import LogAuditoriaViewSet

router = DefaultRouter()
router.register(r'auditoria', LogAuditoriaViewSet, basename='auditoria')

app_name = 'auditoria'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/transaccion/contratos/urls.py`

```python
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .api import ContratoViewSet, TipoContratoViewSet

router = DefaultRouter()
router.register(r'tipos-contrato', TipoContratoViewSet, basename='tipos-contrato')
router.register(r'contratos', ContratoViewSet, basename='contratos')

app_name = 'contratos'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/transaccion/factura/urls.py`

```python
from rest_framework.routers import DefaultRouter
from .api import DetalleFacturaViewSet, EstadoFacturaViewSet, FacturaViewSet

router = DefaultRouter()
router.register(r'estados-factura', EstadoFacturaViewSet, basename='estados-factura')
router.register(r'facturas', FacturaViewSet, basename='facturas')
router.register(r'detalles-factura', DetalleFacturaViewSet, basename='detalles-factura')

app_name = 'factura'

urlpatterns = router.urls
```

### `apps/transaccion/mora/urls.py`

```python
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .api import MoraViewSet

router = DefaultRouter()
router.register(r'moras', MoraViewSet, basename='moras')

app_name = 'mora'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/transaccion/notificaciones/urls.py`

```python
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .api import NotificacionViewSet

router = DefaultRouter()
router.register(r'notificaciones', NotificacionViewSet, basename='notificaciones')

app_name = 'notificaciones'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/transaccion/pagos/urls.py`

```python
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .api import PagoViewSet

router = DefaultRouter()
router.register(r'pagos', PagoViewSet, basename='pagos')

app_name = 'pagos'

urlpatterns = [
    path('', include(router.urls)),
]
```

### `apps/urls.py`

```python
from django.urls import include, path

urlpatterns = [
    path('', include('apps.dashboard.urls')),
    path('', include('apps.catalogos.clientes.urls')),
    path('', include('apps.catalogos.departamentos.urls')),
    path('', include('apps.catalogos.servicios.urls')),
    path('', include('apps.seguridad.usuarios.urls')),
    path('', include('apps.transaccion.contratos.urls')),
    path('', include('apps.transaccion.factura.urls')),
    path('', include('apps.transaccion.pagos.urls')),
    path('', include('apps.transaccion.mora.urls')),
    path('', include('apps.transaccion.notificaciones.urls')),
    path('', include('apps.transaccion.auditoria.urls')),
]
```

## Frontend inspeccionado

- `app.js`: getApiBase, buildQuery, apiGet, fetchDashboard, fetchDashboardVersion, setMessage, setRefreshStatus, setMetric, emptyRows, statusClass, cell, badge, renderInvoiceTable, renderCommercialTable, chartData, upsertChart, renderBilling, renderCustomers, loadDashboards, activateDashboardTab, bindTabs, bindFilters, startVersionPolling
- `auth.js`: softFacturApiOrigin, softFacturFetch, loginPath, csrfToken, csrfHeader, renderSession, rememberActivity, scheduleInactivityLogout, startInactivityWatcher, adminTargetPath, dashboardPath, defaultUserPath, isDashboardPage, toggleRestrictedLinks, applyAdminVisibility, requireSession, logout
- `clientes.js`: initClientsPage, bindEvents, loadInitialData, fetchJson, apiUrl, asList, fillDepartmentSelect, getTodayValue, formatServerDate, formatServerTime, getDepartmentName, getClientFullName, getClientById, createCell, createStatusCell, createActionCell, getFilteredClients, renderClients, updateSummaryCards, updatePreviewFromForm, resetForm, getPayloadFromForm, normalizeNicaraguaPhone, saveClient, editClient, toggleClientStatus, setFormBusy, renderTableMessage, formatApiError
- `configuracion.js`:
- `data.js`: formatoMoneda, obtenerDepartamentoPorId, obtenerClientePorId, obtenerNombreCliente, obtenerServicioPorId, obtenerContratoPorId, obtenerUsuarioPorId, obtenerContratosPorCliente, obtenerDetallesPorFactura, calcularSubtotalLinea, calcularIVA, calcularTotalLinea, calcularTotalesFactura, generarCodigoFactura, obtenerResumenFacturas, obtenerTotalFacturado, obtenerFacturasPendientes, obtenerClientesActivos, obtenerServiciosActivos
- `facturas.js`: createCell, createStatusCell, fetchJson, formatApiError, asList, formatoMoneda, formatInvoiceCode, parseInvoiceCode, toDateInputValue, toInputDateOnly, toApiDate, toApiTime, toLocalDateTime, formatDate, getNextInvoiceCode, fillSelect, getSelectedCustomer, getActiveCustomers, getSelectedTipoContrato, getSelectedService, updateContracts, updateCodeCard, updateDefaultDates, isMonthlyContract, calculateDueDate, updateDueDateFromContract, addService, renderInvoiceItems, updateTotals, getFirstUserId, ensureContractForInvoice, getDetailPayload, syncInvoiceDetails, saveInvoice, setBusy, setInvoiceMode, clearInvoiceForm, getInvoiceStatus, isInvoicePaid, filterInvoicesForHistory, getInvoiceCustomer, renderInvoiceHistory, detailToInvoiceItem, loadInvoiceIntoForm, createInvoiceActionCell, getSelectedContractLabel, renderPreviewItems, renderPrintPreview, applyInvoiceToPreview, revealPrintPreview, showPrintPreview, loadApiData, initUi, initInvoicePage
- `login.js`: apiOrigin, showView, setMessage, clearMessage, postJson, redirectIfAuthenticated
- `moras.js`: loadMorasPage, fetchJson, asList, bindEventsOnce, renderMorasPage, getFilteredMoras, renderMorasTable, createActionCell, getInvoiceCustomer, createCell, createStatusCell, renderTableMessage, countMoraDays, formatInvoiceCode, formatDateTime, formatMoney, formatApiError, setBusy
- `pagos.js`: loadPaymentsPage, fetchJson, asList, initPaymentForm, bindPaymentEventsOnce, fillInvoiceSelect, renderPaymentsPage, hasPaymentForm, getConfirmedPayment, getPaymentForInvoice, getMoraForInvoice, getSuggestedPaymentAmount, getPendingInvoices, renderPendingInvoices, renderPayments, createPaymentActionCell, loadInvoiceForPayment, loadPaymentIntoForm, savePayment, markInvoiceAsPaid, togglePaymentStatus, getPaymentPayload, upsertPayment, clearPaymentForm, setPaymentMode, setBusy, updateSelectedInvoiceSummary, getSelectedInvoice, getInvoiceById, getNextReceiptNumber, getInvoiceCustomer, createCell, createStatusCell, renderTableMessage, formatApiError, formatInvoiceCode, formatDate, formatMoney, getMethodLabel, toDateTimeLocal, toApiDateTime
- `productos.js`: initServicesPage, bindEvents, loadServices, fetchJson, apiUrl, asList, renderServices, createActionCell, saveService, getPayloadFromForm, editService, deleteService, resetForm, updatePreview, setFormBusy, renderTableMessage, createCell, formatApiError, formatMoney
- `reportes.js`: node, localDate, preset, csvCell, download, renderSections, render, load
- `servicios.js`: initServicesPage, bindEvents, loadServices, fetchJson, apiUrl, asList, getFilteredServices, renderServices, updateSummaryCards, createActionCell, saveService, getPayloadFromForm, editService, deleteService, resetForm, updatePreview, setFormBusy, renderTableMessage, createCell, formatApiError, formatMoney
- `theme.js`: refresh
- `usuarios.js`: usersApiOrigin, initUsersPage, bindEvents, loadUsers, getCurrentSessionUser, usersFetch, usersCsrfHeader, asList, isAdmin, applyPermissionState, getFullName, getRoleLabel, formatServerDate, getFilteredUsers, renderUsers, updateSummaryCards, createCell, createRoleCell, createStatusCell, createActionCell, saveUser, validatePasswords, getPayloadFromForm, editUser, toggleUserStatus, resetForm, updatePreview, setFormBusy, renderTableMessage

Templates: `templates/factura_demo.html`; las pantallas operativas residen en
`frontend/Softlutionic/pages/`. No hay formularios Django empresariales separados;
las validaciones viven en serializers, modelos, servicios y JavaScript.

## Infraestructura y dependencias

DB_ENV local/azure sigue seleccionando SQL Server/Azure SQL con el archivo existente.
No se cambiaron pipelines, Gunicorn, infraestructura ni modelos/migraciones.
requirements original: Django, DRF, mssql-django, waitress, WhiteNoise, Gunicorn.
Única nueva dependencia backend: SimpleJWT 5.5.x.
