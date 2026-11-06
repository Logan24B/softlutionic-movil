# Revisión Android de Notificaciones — 2026-11-06

## Cambios de esta revisión

- `MainViewModel.kt`: mensaje específico para HTTP 503 durante la carga.
- `ConsultationController.kt`: botón Reintentar ante fallo de carga o interpretación, deshabilitado mientras se consulta.
- Este informe. El resto del árbol ya tenía cambios anteriores; se conservaron.

## Contrato y limitación

Se consultaron por HTTPS el listado y el schema actuales. Ambos respondieron HTTP 401 con el detalle de credenciales no proporcionadas. No se dispone de una respuesta autenticada actual. El 401 demuestra acceso al servidor; no confirma campos, formato de listado ni operaciones de lectura.

La integración existente utiliza GET `/api/mobile/v1/notificaciones/` mediante `Api.list`, compartiendo Retrofit, OkHttp, Base URL, interceptor Bearer y renovación JWT con los otros módulos. Usa Room como caché; un fallo remoto conserva los registros guardados y muestra el error. La pantalla ya incluye búsqueda, estados de lectura, fecha, detalle, carga y estado vacío.

La marca de lectura existente usa PATCH `/api/mobile/v1/notificaciones/{id}/` con `EstadoNotificacion=true`, condicionada por schema. No se agregó ni se verificó esta ruta como contrato actual. No hay evidencia autenticada de POST/PATCH `/{id}/leer/` ni GET `/no-leidas/count/`; no se agregaron. La pantalla conserva el número de registros cargados, sin presentarlo como contador remoto de no leídas.

No se observaron campos reales de una notificación en esta revisión. El contrato histórico documenta `id`, `ClienteId`, `Fecha_Envio`, `Mensaje`, `EstadoNotificacion`, `cliente_detalle` y `estado_descripcion`; no acredita el JSON actual de MongoDB. No se crearon DTO ni rutas sobre supuestos.

Transporte final conservado: `JsonArray` de `JsonObject`. Modelo de presentación conservado: `NoticeCard(data: JsonObject, client: String, message: String, read: Boolean?)`. Repository sin cambios en esta revisión: valida schema, espera confirmación de PATCH y actualiza Room. Los identificadores siguen como String, sin conversión a Int/Long; solo se reconoce la clave `id`. La clave `_id` requiere contrato confirmado. La fecha existente `Fecha_Envio` se interpreta con OffsetDateTime y se muestra en la zona del dispositivo; una fecha no interpretable muestra «Fecha no disponible».

No se añadieron credenciales, conexiones MongoDB, mocks ni datos ficticios. No se modificaron backend, Azure, MongoDB ni API. No se hizo commit ni push.

Pendiente para completar la integración actual: JSON autenticado de listado, contrato real de contador y marca de lectura, y validación de lectura/escritura con una sesión de prueba. No se afirma funcionamiento remoto completo.

## Comprobación autenticada posterior

Se recibió autorización mediante un token temporal proporcionado por el usuario. Se utilizó solo en memoria, sin guardarlo ni incluirlo en el APK.

- GET `/api/mobile/v1/notificaciones/`: HTTP 503, `{"detail":"Servicio de notificaciones temporalmente no disponible."}`.
- GET `/api/mobile/v1/schema/`: HTTP 200; módulo `notificaciones` con permisos `read=true`, `create=false`, `update=false`, `delete=false`, y `fields=[]`.
- GET `/api/mobile/v1/notificaciones/no-leidas/count/`: HTTP 503 con el mismo detalle. Esto no confirma el contrato del contador: el servicio puede fallar antes de resolver la acción.

El schema actual bloquea la marca de lectura existente en Android; no se ejecutaron escrituras ni se probó `/leer/` con identificadores inventados. El único campo observado en la respuesta del listado fue `detail` de error, no campos de documentos. El 503 impide verificar lista real, DTO de MongoDB, contador y lectura. La solución del servicio remoto queda fuera del alcance Android autorizado.

## Validación local

- `.\gradlew.bat test`: BUILD SUCCESSFUL.
- `.\gradlew.bat assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- `git diff --check`: sin errores.
- Se detectó un emulador conectado. No se instaló la aplicación ni se ejecutaron instrumentaciones con mocks. No se verificaron visualmente los cambios ni se simularon respuestas 403/503; el 503 y el 401 se observaron en consultas HTTPS reales. Las pruebas locales existentes no demuestran funcionamiento remoto.
