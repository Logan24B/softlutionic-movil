# Contacto móvil

Implementación local de consulta administrativa de `solicitudes_contacto`.

## Backend

- Reutiliza `apps/transaccion/notificaciones/mongo.py::collection()` y su MongoClient, base configurada y `MONGODB_URI`.
- Endpoint: `GET /api/mobile/v1/solicitudes-contacto/`, JWT obligatorio; administrador por `Rol` o `is_superuser`. `is_staff` no concede acceso.
- Solo lectura; listado ordenado por `fecha_creacion` y `_id`, descendentes.
- Archivos creados: `apps/mobile_api/contact_requests.py`, `test_contact_requests.py`.
- Archivos modificados: `apps/mobile_api/registry.py`, `auth.py`, `api.py`, `urls.py`.
- El schema incluye el módulo únicamente para roles autorizados, con read=true y escrituras=false.
- Ante indisponibilidad Mongo: HTTP 503 controlado y registro interno con traceback sin texto sensible del driver.

La inspección real encontró estas claves (todas presentes en los 8 documentos): `_id`, `tipo`, `origen`, `nombre`, `correo`, `telefono`, `asunto`, `mensaje`, `estado`, `prioridad`, `asignado_a`, `fecha_creacion`, `notificacion`, `historial`. Las claves están en minúsculas y sin acentos; la captura traducida no reflejaba su escritura exacta.

Se exponen `id` (conversión de `_id` a String), `nombre`, `correo`, `telefono`, `asunto`, `mensaje`, `estado`, `prioridad`, `origen`, `tipo` y `fecha_creacion`. Los demás campos internos no se exponen. Opcionales ausentes: null; fechas: ISO 8601 UTC.

Ejemplo sanitizado de estructura, no utilizado como dato de la aplicación:

```json
{
  "id": "<ObjectId como cadena>",
  "nombre": "<nombre omitido>",
  "correo": "<correo omitido>",
  "telefono": "<telefono omitido>",
  "asunto": "<asunto omitido>",
  "mensaje": "<mensaje omitido>",
  "estado": "pendiente",
  "prioridad": "normal",
  "origen": "pagina_web",
  "tipo": "contacto_web",
  "fecha_creacion": "<fecha ISO 8601>"
}
```

Verificación local: Django APIClient con JWT real generado para usuarios de autenticación en SQLite en memoria, y consulta real de MongoDB Atlas. Administrador: 200 y 8 solicitudes, con los once campos completos. Anónimo: 401; empleado: 403; POST administrativo: 405. No se escribieron documentos Mongo ni usuarios en SQL Server. Esto no acredita despliegue ni autenticación de producción en Azure.

Suite backend: 46 pruebas aprobadas (`apps.mobile_api apps.transaccion.factura`, settings `apps.mobile_api.test_settings`, FCM deshabilitado).

## Android

Archivos creados:

- `data/remote/ContactRequestDto.kt`: ID String, campos opcionales nullable.
- `ui/contact/ContactUiState.kt`: Loading, Success, Empty, Error.
- `ui/contact/ContactRequestsController.kt`: RecyclerView, búsqueda local por nombre/correo/asunto/mensaje y detalle de campos presentes.
- `res/layout/view_contact_requests.xml`, `res/values/contact.xml`, `res/drawable/ic_contact.xml`.
- `src/test/java/com/softlutionic/mobile/ContactAuthorizationTest.kt`.

Archivos modificados: `MainActivity.kt`, `viewmodel/MainViewModel.kt`, `data/repository/SoftlutionicRepository.kt`, `domain/auth/AuthorizationPolicy.kt`.

Reutiliza el Retrofit/OkHttp/JWT existente mediante `Api.list(module)`, el Repository compartido, MainViewModel y Room. La visibilidad requiere permiso del schema y rol administrativo. El Repository bloquea escrituras antes de Retrofit. Ningún componente Android conoce MongoDB ni sus credenciales.

La pantalla usa el menú y tema existentes, muestra nombre/asunto/fecha/estado disponibles, y abre un detalle con correo, teléfono y mensaje completo. Omite campos null. Fechas ISO se convierten a la zona del dispositivo. Refrescar reutiliza la barra existente; errores ofrecen Reintentar. HTTP 401 conserva el manejo de sesión existente; 403 deniega; 503 informa indisponibilidad. La búsqueda no genera llamadas de red.

El menú depende del schema del backend. Como este cambio no se ha desplegado, el backend Azure actual no anuncia todavía Contacto. No se agregó un módulo ficticio ni datos simulados para hacerlo aparecer. La navegación y datos reales en el dispositivo contra Azure quedan pendientes de un despliegue autorizado.

Validación Android: `gradlew.bat test` aprobado, 150 pruebas, cero fallos y cero omitidas. El primer intento restringido encontró AccessDeniedException al leer el SDK `android.jar`; la ejecución con acceso al SDK pasó. No se interpretó ese bloqueo como un fallo de lógica.

`gradlew.bat assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL. APK de aplicación actualizado en emulator-5554 mediante `adb install -r`: Success, tras comprobar que el certificado SHA-256 coincide con el de la instalación existente. No se desinstaló ni borró información del usuario. No se ejecutó instrumentación ni se verificó visualmente Contacto con datos en Android, ya que el backend remoto no se actualizó. La generación del APK de pruebas no equivale a ejecutar esas pruebas.

`git diff --check` pasó en ambos proyectos.

## Alcance preservado

Android no se conecta directamente a MongoDB y no contiene MONGODB_URI. No se modificaron softlutionic-api2, el formulario Contáctenos, GitHub Actions, SQL Server ni la colección notificaciones. La implementación y validación inicial se realizaron sin commit, push ni cambios en Azure. El archivo privado local con la URI está ignorado por Git.

## Publicación autorizada posteriormente

El usuario autorizó commit y push después de la validación local. Se publican el backend en `Logan24B/Softlutionic`, rama `master`, y Android en `Logan24B/softlutionic-movil`, rama `main`. El workflow existente del backend se activa con ese push y realiza el despliegue en Azure. Las verificaciones locales descritas arriba no equivalen a una consulta autenticada de producción después del despliegue.
