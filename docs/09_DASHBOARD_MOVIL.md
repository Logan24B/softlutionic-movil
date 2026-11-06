# Dashboard ejecutivo móvil

La pantalla reutiliza MainActivity, MainViewModel, Retrofit y la caché Room existentes.
No se modificó Django, JWT, SessionManager ni las rutas de la API. No se añadieron dependencias.

## Contrato y presentación

Se inspeccionó en modo lectura el código local de `apps/dashboard/api.py`,
`apps/mobile_api/urls.py`, `registry.py` y `auth.py` del backend de referencia.
Esto verifica el contrato del código disponible, no el despliegue actual de Azure.

- `dashboard/facturacion/`: `cards`, series `facturado_por_mes` y
  `facturas_por_estado`, y `tables.facturas` con código, fecha, monto y estado.
- `dashboard/clientes-servicios/`: cuatro indicadores operativos, ingresos por
  servicio, contratos por tipo y clientes por departamento.
- Los importes se presentan con BigDecimal y NumberFormat: `C$5,868.00`.
  Un valor ausente se muestra como `—`; un cero real, como `C$0.00`.
- Las series conservan exactamente los períodos y estados recibidos, sin rellenar meses.
  Canvas dibuja barras y donuts. Las leyendas incluyen todos los valores y son
  accesibles; tocar una barra muestra su valor. Las series largas permiten scroll horizontal.
- Se muestran hasta seis facturas recientes de la tabla del dashboard, ordenadas por
  fecha. No se hace una consulta adicional a Facturas. «Ver todas» aparece solo
  con permiso de lectura del módulo correspondiente.

## Navegación y permisos

El Bottom Sheet muestra únicamente módulos de schema con `permissions.read`.
El dashboard depende del booleano explícito `dashboard` recibido en la identidad
y almacenado por el repositorio. No se infiere autorización de la presencia de Usuarios.
Seleccionar un módulo reutiliza `MainViewModel.select()`. Crear, editar, eliminar y
cerrar sesión conservan el flujo existente y sus permisos. Los formularios conservan
sus selectores de relaciones; solo se eliminó el Spinner de navegación principal.

## Filtros y límites reales del backend

Los endpoints existentes reciben parámetros opcionales `start` y `end` en ISO.
Un Bottom Sheet abre MaterialDatePicker de rango. Aplicar y limpiar consultan al
servidor; no se realiza filtrado local que pueda alterar las sumas.
La caché usa claves separadas por período (`dashboard:desde:hasta`) para evitar
mostrar un resumen general como si perteneciera a un rango.

El código backend inspeccionado filtra facturas, cobros, mora e ingresos relacionados.
**Clientes y contratos, contratos por tipo y clientes por departamento son globales**,
incluso al elegir período. La interfaz explica este alcance. «Ingresos por servicio»
es el agregado proporcionado por el backend, que incluye hasta diez servicios;
no se presenta como un total universal. La tabla de origen devuelve hasta doce facturas.

## Validación

Pruebas de contrato JSON, precisión de moneda, períodos sin datos artificiales,
estados reales y valores ausentes en `DashboardDataTest`. Se mantienen las pruebas
anteriores de mapeo de registros. Ejecutar `gradlew.bat test`, `assembleDebug` e
`installDebug` cuando haya un emulador autorizado.

`DashboardSmokeInstrumentation` vive exclusivamente en `androidTest`. Comprueba
renderizado financiero, comercial, vacío, ancho 320dp/texto 1.4 y layout del menú.
Sus cifras son fixtures de prueba y no forman parte de la aplicación distribuida.
Para repetir: compilar `assembleDebugAndroidTest`, instalar el APK de pruebas y
ejecutar `adb shell am instrument -w com.softlutionic.mobile.test/com.softlutionic.mobile.DashboardSmokeInstrumentation`.
Genera capturas identificadas como `*-test-fixture.png` en archivos externos de la app,
y cierra la actividad de prueba al terminar, sin cambiar sesión ni caché.

La comprobación de métricas reales y permisos en Azure requiere una sesión
autenticada; no se incorporaron credenciales ni datos de demostración en producción.

## Archivos de este cambio

Modificados:

- `app/build.gradle.kts`: runner de la prueba visual, sin dependencias adicionales.
- `app/src/main/java/com/softlutionic/mobile/MainActivity.kt`: renderizado, barra,
  navegación por Bottom Sheet, filtros y estados de sincronización.
- `app/src/main/java/com/softlutionic/mobile/viewmodel/MainViewModel.kt`: permiso
  explícito del dashboard, período y observación de caché por rango.
- `app/src/main/java/com/softlutionic/mobile/data/remote/Api.kt`: parámetros de
  consulta opcionales sobre las mismas rutas de dashboard.
- `app/src/main/java/com/softlutionic/mobile/data/repository/SoftlutionicRepository.kt`:
  propagación del rango y claves de caché, conservando el resto del repositorio.
- `app/src/main/java/com/softlutionic/mobile/ui/RecordAdapter.kt`: apariencia oscura.
- `app/src/main/res/layout/activity_main.xml` y `view_records.xml`: contenedor oscuro,
  barra y sustitución del selector de navegación.

Nuevos:

- `app/src/main/java/com/softlutionic/mobile/ui/DashboardData.kt`: contrato y formato.
- `app/src/main/java/com/softlutionic/mobile/ui/DashboardRenderer.kt`: paneles reutilizables.
- `app/src/main/java/com/softlutionic/mobile/ui/FinancialChartView.kt`: gráficos Canvas.
- `app/src/main/res/layout/view_dashboard.xml`, `item_kpi.xml`, `bottom_sheet_modules.xml`.
- `app/src/main/res/values/dashboard.xml`: textos, colores y estilos.
- `app/src/main/res/drawable/ic_dashboard.xml`, `ic_invoice.xml`, `ic_modules.xml`,
  `ic_paid.xml`, `ic_pending.xml`, `ic_refresh.xml`, `ic_services.xml`.
- `app/src/test/java/com/softlutionic/mobile/DashboardDataTest.kt`.
- `app/src/androidTest/java/com/softlutionic/mobile/DashboardSmokeInstrumentation.kt`.
- Este documento.

Los cambios locales del login preexistentes se conservaron. No se modificó el
logo, SessionManager ni el backend en esta tarea. Los archivos `.vscode` locales
ajenos a este cambio se dejaron intactos.

## Resultado comprobado

- `gradlew.bat test`: 7 pruebas unitarias aprobadas (3 del dashboard, 4 existentes).
- `gradlew.bat assembleDebug`: BUILD SUCCESSFUL.
- `gradlew.bat installDebug`: instalado en Pixel_9 (AVD), Android 17.
- Prueba instrumentada aislada: resultado satisfactorio (`INSTRUMENTATION_CODE: -1`).
- Capturas revisadas del resumen y gráficos en `app/build/dashboard-test-fixture.png`
  y `app/build/dashboard-charts-test-fixture.png`, con fixtures exclusivamente de prueba.
- No se ejecutó commit ni push. La validación de extremo a extremo contra Azure con
  sesión real no se realizó; no se proporcionaron credenciales para esa comprobación.
