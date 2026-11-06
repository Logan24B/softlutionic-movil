# Tipos de contrato Android

Cambios exclusivos en `C:\proyectos\SoftlutionicAndroid`. Sin modificaciones en backend, Azure, endpoints, modelos ni web; sin commit ni push.

## Contrato real

Consultas GET autenticadas a `schema/` y `tipos-contrato/` mediante `ContractTypesContractInstrumentation` en Pixel 9: tres registros, claves `id`, `Nombre`, `Descripcion`. No se imprimieron tokens ni valores del catálogo.

| Campo | Tipo | Obligatorio | Longitud | Uso |
| --- | --- | --- | --- | --- |
| Nombre | CharField | Sí | 80 | Editable si el schema y la política permiten escribir |
| Descripcion | CharField | No | 250 | Editable si el schema y la política permiten escribir |
| id | Clave técnica del GET, ausente de fields editables | No se solicita | — | Solo interno para CRUD, caché y relaciones |

No se anunciaron relaciones. El usuario auditado recibió read/create/update/delete=true. Esto no se toma como autorización universal: cada pantalla calcula permisos efectivos a partir del schema del usuario actual AND la política local. Descripción se mantiene opcional aunque un ejemplo visual sugiera un asterisco.

## Presentación

`ContractTypePresentation` expone solo nombre y descripción. El objeto JSON original se conserva para utilizar internamente su ID. No se muestra `id`, `#id`, etiquetas técnicas, ni un título que repita Nombre en las tarjetas. No se solicita ID en creación/edición.

`ContractTypesController` incorpora:

- Título TIPOS DE CONTRATO, subtítulo solicitado y estilo navy/violeta/cian.
- Dos MaterialButton independientes, 63/37 %, 52 dp de alto, 10 dp de separación, esquinas de 14 dp y ripple propio. Nuevo tipo usa violeta sólido y texto/icono blanco; Actualizar tiene fondo navy, borde e icono cian.
- Tarjetas navy elevadas, esquinas de 18 dp, padding de 16 dp y separación de 12 dp. Nombre principal, descripción de hasta tres líneas con ellipsis y acceso al detalle.
- Búsqueda local por Nombre/Descripcion, ignorando mayúsculas y acentos. Ninguna llamada HTTP por carácter.
- Detalle con nombre y descripción completa, sin ID técnico. Editar/Eliminar se ocultan según permiso efectivo.
- Bottom Sheet oscuro y desplazable para crear/editar; solo campos editables del schema, precarga, descripción multilínea, validación de requeridos/longitud y errores de campo. Estado Guardando y prevención de doble pulsación.
- Confirmación de eliminación y mensaje para 409: “No se puede eliminar porque existen contratos relacionados.”
- Empty state con acción de creación solo cuando está permitida. Empleado ve “No hay tipos de contrato disponibles.” sin acciones de escritura.
- Room y refresco compartido. Una respuesta confirmada de escritura se mantiene si el GET posterior falla.

## Autorización y relación con Contratos

No se modificó la clasificación de roles ni `AuthorizationPolicy`. Empleado conserva consulta, búsqueda y refresco; create/update/delete=false incluso si schema anuncia true. Admin y Superusuario conservan las operaciones autorizadas por schema.

Los controles de la pantalla revalidan permisos; MainViewModel rechaza las mutaciones de los módulos de solo lectura para empleados antes de iniciar runTask o llamar al Repository. El Repository mantiene su segunda comprobación previa a Retrofit. La misma política central protege Servicios.

`repository.options("tipos-contrato")`, los registros almacenados y los IDs permanecen intactos. Contratos sigue resolviendo TipoContratoId a Nombre y usando el ID seleccionado en su payload. No se modifica su formulario ni su catálogo de relaciones.

## Archivos de este cambio

Nuevos:

- `app/src/main/java/com/softlutionic/mobile/ui/contracttypes/ContractTypePresentation.kt`
- `app/src/main/java/com/softlutionic/mobile/ui/contracttypes/ContractTypesController.kt`
- `app/src/test/java/com/softlutionic/mobile/ContractTypePresentationTest.kt`
- `app/src/androidTest/java/com/softlutionic/mobile/ContractTypesContractInstrumentation.kt`
- `app/src/androidTest/java/com/softlutionic/mobile/ContractTypesSmokeInstrumentation.kt`
- Este documento y capturas en `docs/verification/contract-types/`.

Modificados: MainActivity (integración y observadores), MainViewModel (búsqueda, estado de guardado y comprobación local antes del Repository), SoftlutionicRepository (conserva escritura confirmada si falla el refresco posterior).

Api, SessionManager, modelos remotos, RecordMapper, RecordAdapter y política central de roles permanecen sin cambios en esta tarea. Se reutilizan los recursos y el script de firma compatible de Contratos.

## Validación

63 tests unitarios aprobados, cero fallos/errores, incluidos ocho nuevos de presentación, búsqueda, descripción completa, exclusión de ID/read-only del payload, requeridos/longitudes, permisos de los tres roles y resolución del tipo desde Contratos.

```powershell
.\gradlew.bat test assembleDebug -I scripts/contracts-validation.init.gradle
.\gradlew.bat installDebug installDebugAndroidTest -I scripts/contracts-validation.init.gradle '-PcontractsRunner=com.softlutionic.mobile.ContractTypesSmokeInstrumentation'
adb shell am instrument -w com.softlutionic.mobile.test/com.softlutionic.mobile.ContractTypesSmokeInstrumentation
git diff --check
```

La firma debug local compatible permite actualizar la app del Pixel 9 sin borrar datos ni sesión. Las pruebas visuales usan registros sintéticos; un proxy impide cualquier mutación real de la API. Las consultas reales verifican lectura/schema, sin pretender validar escrituras reales en Azure.

Resultado de instrumentación: `INSTRUMENTATION_CODE: -1`, tres escrituras de Empleado rechazadas síncronamente antes del Repository y cero llamadas de mutación a Api. Verificados listado/detalle sin ID, búsqueda, empty state de empleado, acciones de Admin/Superusuario, creación vacía con validación, precarga de edición, ancho de 320 dp, fuente al 130 % y formulario con teclado. Capturas revisadas en `docs/verification/contract-types/`. Compilación e instalación correctas y `git diff --check` sin errores.
