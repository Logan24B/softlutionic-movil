# Notificaciones: adaptación Android al contrato MongoDB

La API ya devuelve registros, pero Android buscaba los campos SQL anteriores. Esta revisión adapta únicamente Android al serializer y ViewSet inspeccionados en `apps/mobile_api/notifications.py` del backend local.

- GET `/api/mobile/v1/notificaciones/`: listado existente, sin cambios de URL ni autenticación.
- GET `/api/mobile/v1/notificaciones/no-leidas/count/`: contador `count` entero no negativo, recibido del servidor. Ante fallo no se inventa un valor ni se oculta el listado correcto.
- PATCH `/api/mobile/v1/notificaciones/{id}/leer/`: acción sin cuerpo; espera `id` coincidente y `leida=true` antes de reemplazar el registro en Room y volver a consultar el contador.

El serializer expone `id`, `tipo`, `titulo`, `mensaje`, `origen`, `prioridad`, `estado`, `fecha_creacion`, `referencia` y `leida`. Son campos verificados en código de backend; esta revisión no obtuvo una muestra JSON autenticada actual. Se conserva transporte JsonArray/JsonObject y se utiliza NoticeCard con título, mensaje, fecha y estado nullable. El ID es String; no se convierte a Int/Long. OffsetDateTime interpreta ISO-8601 y muestra la fecha en la zona del dispositivo.

La UI mantiene las tarjetas, filtros y detalle. Muestra título, mensaje, fecha y estado de lectura. La búsqueda admite título/mensaje. Se elimina la etiqueta de cliente: este contrato no proporciona un cliente. Tipo y prioridad aparecen en el detalle si existen. No se consulta el catálogo de clientes para este módulo.

El permiso genérico `update=false` del schema impide editar registros, pero no representa el permiso de `/leer/`: el ViewSet permite esa acción a usuarios autenticados y filtra el destinatario en el servidor. Android exige schema con lectura y sesión; Django conserva la autoridad y resuelve 401/403/404/503. La UI conserva carga, vacío, error y reintento.

Archivos de esta revisión: Api.kt, SoftlutionicRepository.kt, MainViewModel.kt, MainActivity.kt, ConsultationPresentation.kt, ConsultationController.kt, ConsultationPresentationTest.kt y este informe. No se modifican backend, Azure, MongoDB ni credenciales. No se hace commit ni push.

Validación: `.\gradlew.bat test` correcto (148 pruebas, cero fallos); `.\gradlew.bat assembleDebug assembleDebugAndroidTest` correcto; `git diff --check` correcto. Se actualizó el emulador sin borrar datos. El APK recién generado usaba otra firma y produjo INSTALL_FAILED_UPDATE_INCOMPATIBLE; se firmó una copia `app-debug-compatible.apk` con la clave debug existente, se verificó que su certificado coincidiera con el instalado y `adb install -r` terminó Success. No se ejecutaron instrumentaciones con API simulada ni se confirmó visualmente el resultado remoto. El instrumento histórico ConsultationSmokeInstrumentation todavía describe el contrato SQL anterior y no acredita esta integración MongoDB. Falta comprobar en una sesión real el listado, contador y PATCH de lectura desde la app actualizada.
