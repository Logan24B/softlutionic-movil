# Análisis — módulo local en desarrollo

Se agrega Análisis al menú principal mediante availableNames/moduleLabel/icono en MainActivity, sin depender de un módulo del schema remoto. Es información estática accesible desde la sesión móvil, no una concesión de acceso a datos analíticos.

La pantalla usa ViewAnalysisBinding y AnalysisController, siguiendo el patrón de los controladores actuales. Reutiliza el fondo, la tipografía, MaterialCardView, botones, badges y el constructor de tarjetas InvoiceViews. Incluye los textos solicitados, las nueve categorías previstas y referencias a próximas funciones, sin valores, gráficos ni rankings simulados.

MainViewModel conserva la selección existente y evita refresh remoto para analisis. La barra no muestra refresco ni errores de sincronización de otros módulos mientras se está en esta pantalla. No hay API, DTO ni Repository analítico. La futura integración deberá ser HTTPS/API analítica/DW_Sofactur, nunca una conexión directa desde Android.

El botón Volver y el botón atrás del sistema regresan al módulo previo disponible; el menú sigue permitiendo navegar a los demás módulos. Se guarda la ruta Análisis y su destino de regreso al recrear la Activity.

Archivos creados: ui/analysis/AnalysisController.kt, res/layout/view_analysis.xml, res/values/analysis.xml, res/drawable/ic_analysis.xml y este informe. Archivos modificados: MainActivity.kt y MainViewModel.kt.

Este trabajo no modifica backend, Azure, API, bases de datos, conexiones, endpoints ni GitHub Actions. No se hizo commit ni push. El módulo Contacto solicitado posteriormente requiere confirmar los campos del documento real antes de crear su contrato.

Validación: `test` correcto (149 pruebas) y `assembleDebug` correcto. La revisión visual detectó y corrigió que la opción local podía convertirse en pantalla inicial antes de recibir el schema; ahora se espera al menú existente para elegir el módulo por defecto. Se repitieron pruebas/compilación tras esa corrección y se instaló el APK final con firma compatible, conservando los datos. Se observó Análisis en el menú, la tarjeta En desarrollo, las nueve categorías y Próximamente sin valores ni gráficos simulados. Se navegó desde Clientes a Análisis y se verificó la carga existente de Clientes. El botón Volver se comprobó visualmente; su activación y el botón atrás del sistema no se verificaron completamente por una limitación de coordenadas del control lateral del emulador. No se midió tráfico HTTP con un capturador: la ausencia de llamadas analíticas se acredita por el controlador local y la guarda en MainViewModel.refresh. `git diff --check` sin errores.
