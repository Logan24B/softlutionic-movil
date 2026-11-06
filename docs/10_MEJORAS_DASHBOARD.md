# Dashboard: filtros y lectura financiera

Archivos modificados:
- MainActivity.kt: hoy, ayer, anteayer, semana desde lunes, mes hasta hoy y rango personalizado. Usa la fecha local del dispositivo y los parámetros start/end existentes. El selector permite desplazamiento en pantallas pequeñas. Muestra el rango analizado y el alcance de los indicadores.
- DashboardRenderer.kt: encabezado financiero, subtítulos, selector de mes persistente mientras ese mes siga disponible, espacio y desplazamiento para departamentos.
- FinancialChartView.kt: etiquetas completas a 60 grados, altura y margen derecho calculados según el nombre más largo. Mantiene leyendas y selección accesible de barras.
- dashboard.xml: encabezado de indicadores de facturación.
- DashboardSmokeInstrumentation.kt: presencia del selector y departamentos largos a ancho reducido.

El mes filtra exclusivamente la serie agregada recibida, dentro del rango activo; no altera los KPI ni obtiene meses ausentes. Los totales dependen del filtrado remoto existente. Clientes y contratos mantienen el alcance global explicado en pantalla. No se modificaron endpoints ni backend.

Para probar: abrir Dashboard, Filtrar período y probar cada acceso rápido, rango personalizado y Limpiar. Verificar el rango visible y el total tras terminar la consulta. Elegir un mes y regresar a todos los meses. En Clientes y servicios, revisar departamentos largos y desplazamiento horizontal, también con pantalla estrecha. Verificar estados vacíos y funcionamiento sin conexión con caché por rango.

## Validación de esta entrega
- testDebugUnitTest: 7 pruebas, cero fallos.
- assembleDebug y assembleDebugAndroidTest: compilación satisfactoria.
- La instalación en el emulador fue rechazada con INSTALL_FAILED_UPDATE_INCOMPATIBLE: la app existente usa otra firma. Se conservó la instalación y sus datos; no se ejecutaron las pruebas instrumentadas ni se verificó visualmente esta entrega.
- La verificación de importes filtrados en Azure requiere sesión autenticada.
