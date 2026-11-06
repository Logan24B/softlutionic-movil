# Integración de Notificaciones Android — 2026-11-06

## Alcance y evidencia

Solo se modificó Android. Se preservó el árbol de trabajo previo. No se modificaron Django, MongoDB, Azure, web, endpoints ni modelos del servidor. No se hizo commit ni push.

La revisión cubrió Api.kt, Network.kt, SessionManager.kt, Repository, MainViewModel, MainActivity, ConsultationController/Presentation, mapper, navegación, Manifest y configuración Gradle. Se contrastó el contrato guardado en docs/23_MORAS_NOTIFICACIONES_AUDITORIA.md y docs/verification/consultation/contract.txt. Ese contrato es histórico: no demuestra el contrato actual de MongoDB.

## Contrato disponible

- GET `api/mobile/v1/notificaciones/`, mediante `Api.list("notificaciones")`, espera un array JSON.
- PATCH `api/mobile/v1/notificaciones/{id}/`, mediante `Api.update`, envía exclusivamente `{"EstadoNotificacion":true}` y espera el objeto actualizado.
- GET `api/mobile/v1/schema/` confirma capacidades y campos editables en ejecución.
- No se encontró un endpoint de contador de no leídas en el contrato Android. No se inventó una ruta ni un contador global.

El contrato guardado documenta `id`, `ClienteId`, `Fecha_Envio`, `Mensaje`, `EstadoNotificacion`, `cliente_detalle` y `estado_descripcion`. La muestra remota histórica estaba vacía: no se debe presentar esta lista como una respuesta real observada hoy. No hay evidencia disponible de título, categoría ni referencia a factura en la respuesta actual.

Se conserva el modelo dinámico existente: `JsonArray`/`JsonObject` en transporte y caché, `NoticeCard(data, client, message, read: Boolean?)` en presentación. No se creó un DTO tipado sobre campos sin confirmar. Se conservan propiedades desconocidas y null en JSON; los accesos de presentación comprueban el tipo y muestran textos de dato no disponible. `id` se maneja como String, sin convertirlo a Int/Long. Un ObjectId expuesto en `id` como string es compatible; un campo `_id` distinto requiere confirmar el contrato antes de adaptarlo. Las fechas ISO-8601 con offset se muestran en la zona del dispositivo mediante OffsetDateTime.

## Conexión y estados

Se reutiliza createApi y BuildConfig.BASE_URL, cuyo valor predeterminado es `https://softlutionic-sistema-eqh8cjdbbre4fndd.westus-01.azurewebsites.net/`. No se agregó otra URL ni cliente MongoDB.

AuthInterceptor añade `Authorization: Bearer <access>` desde SessionManager. TokenAuthenticator renueva mediante el refresh de la misma sesión. SessionManager conserva los tokens cifrados con Android Keystore/AES-GCM. Estos mecanismos no se modificaron.

MainViewModel.refresh → Repository.refresh → Api.list → Room → StateFlow content → ConsultationController.submit. La búsqueda y los filtros de lectura afectan la presentación de los registros recibidos; no filtran autorización por usuario. Las capacidades siguen procediendo del schema del servidor.

La pantalla conserva tarjetas, badges de lectura, detalle, búsqueda y navegación. No muestra identificadores técnicos. La carga usa busy; el resultado se observa desde Room; un listado vacío muestra «No tienes notificaciones por el momento.»; un fallo de consulta se presenta como error y permite reintentar con Actualizar. Se distinguen 401, 403, 404, otros HTTP, errores de conexión y de interpretación. Si hay caché, puede seguir visible con el aviso de fallo de actualización.

Al abrir una notificación no leída, se solicita la marca de lectura cuando el schema lo permite. Se mantiene el botón del detalle para reintentar explícitamente. MainViewModel bloquea dobles envíos y exige conexión. Repository consulta schema y el listado autorizado antes de escribir; comprueba la respuesta PATCH, incluyendo id y estado boolean true, y reemplaza el elemento de Room sin volver a descargar el listado después del PATCH. La UI se actualiza por StateFlow, sin lectura optimista ni recarga de toda la aplicación. Un fallo no simula éxito. No se generan notificaciones locales.

## Archivos de esta intervención

- app/src/main/java/com/softlutionic/mobile/MainActivity.kt
- app/src/main/java/com/softlutionic/mobile/viewmodel/MainViewModel.kt
- app/src/main/java/com/softlutionic/mobile/data/repository/SoftlutionicRepository.kt
- app/src/main/java/com/softlutionic/mobile/ui/consultation/ConsultationController.kt
- docs/24_INTEGRACION_NOTIFICACIONES.md

## Limitaciones actuales

Validación local: `.\gradlew.bat test` finalizó BUILD SUCCESSFUL, con 147 pruebas, 0 fallos y 0 errores. `.\gradlew.bat assembleDebug assembleDebugAndroidTest` finalizó BUILD SUCCESSFUL. `git diff --check` terminó sin errores. Los APK se generaron, pero no se instalaron ni se ejecutaron instrumentaciones porque no había dispositivo conectado. Las pruebas existentes no acreditan el contrato remoto actual ni las nuevas interacciones en un dispositivo.

El GET remoto falló durante TLS: Invoke-WebRequest informó un error de recepción y curl/Schannel devolvió SEC_E_NO_CREDENTIALS. No se recibió HTTP ni JSON. adb devices no encontró dispositivos. No se verificaron respuesta autenticada actual, deserialización de documentos reales MongoDB, lectura real ni contador remoto. Se solicitó el JSON/rutas reales o una sesión de prueba para completar esa validación. No se usaron mocks para suplir la evidencia remota ni se ejecutaron instrumentaciones con API simulada.
