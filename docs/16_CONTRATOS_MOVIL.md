# Contratos Android

Trabajo limitado a `C:\proyectos\SoftlutionicAndroid`. Sin cambios en Django, Azure, endpoints, modelos ni web. Sin commit ni push.

## Contrato real verificado

Se consultaron con la sesión existente del Pixel 9 `GET /api/mobile/v1/schema/`, `contratos/`, `clientes/` y `tipos-contrato/`, mediante `ContractsContractInstrumentation`. No se imprimieron tokens ni datos personales. Se recibieron dos contratos. Las capacidades del usuario consultado fueron read/create/update/delete=true; la app utiliza el schema del usuario actual, sin asumir que todos tienen esas capacidades.

| Campo | Tipo real | Obligatorio | Editable |
| --- | --- | --- | --- |
| ClienteId | PrimaryKeyRelatedField, clientes | Sí | Según schema |
| TipoContratoId | PrimaryKeyRelatedField, tipos-contrato | No | Según schema |
| Fecha_Inc | DateField | Sí | Según schema |
| Fecha_Fin | DateField | Sí | Según schema |
| EstadoContrato | BooleanField | No | Según schema |
| Descripcion | CharField, máximo 250 | Sí | Según schema |

El GET también devuelve `id`, `cliente_detalle`, `tipo_contrato_detalle` y `estado_descripcion`. Son datos de consulta; no se envían en el formulario. El ID es generado por servidor. El schema no anuncia fechas automáticas para Contratos: ambas fechas se solicitan al usuario. No se crean fechas a partir del reloj del dispositivo. Si un campo pasa a read-only en el schema queda excluido del payload.

Tipo de contrato es opcional: no se convierte en obligatorio localmente. Puede elegirse un tipo existente aunque el usuario tenga ese catálogo en modo de solo lectura. El booleano opcional puede omitirse para conservar el valor predeterminado del servidor; no se inventa un valor inicial.

## Presentación

- Encabezado y subtítulo específicos, navy oscuro, tarjetas elevadas de 18 dp, cliente en primer plano, tipo legible, descripción de hasta tres líneas y detalle completo.
- Dos MaterialButton independientes: Nuevo contrato (63 %, violeta sólido, texto blanco, icono add) y Actualizar (37 %, borde cian), altura 52 dp, separación 10 dp, esquinas 14 dp y ripple individual.
- Búsqueda local por cliente, tipo, descripción o número de contrato; filtros de cliente y tipo en Bottom Sheet. No se consulta la API por cada carácter.
- Relaciones resueltas desde los catálogos del Repository y, como respaldo, los objetos `cliente_detalle`/`tipo_contrato_detalle` recibidos. Cliente utiliza Nombre + Apellido. Nunca se presenta una FK numérica como nombre.
- Formularios oscuros de creación/edición en Bottom Sheet desplazable, selectores Material de relaciones, fechas ISO con calendario opcional, estado booleano y descripción. Solo se envían los IDs reales y campos editables del schema.
- Selección inicial de relaciones, valores y fechas preservados al editar. Si falta una relación guardada se bloquea Guardar para evitar perderla accidentalmente.
- Validación de requeridos, fecha válida y longitud. Errores de campo del backend, estado Guardando, prevención de doble pulsación, cierre tras confirmación y refresco compartido. Una escritura confirmada conserva su respuesta en caché si falla el GET posterior.
- Detalle completo con sección de sistema; eliminación con confirmación y mensaje específico para HTTP 409.
- Crear/Editar/Eliminar dependen de permisos efectivos. Repository vuelve a comprobar schema antes de escribir. No se añade ninguna restricción de rol a Contratos ni se cambian las reglas de Usuarios, Servicios o Tipos de contrato.
- Room mantiene contratos y catálogos para consulta sin conexión. Un fallo de catálogo no elimina los datos visibles. Búsqueda y filtros se conservan en ViewModel durante recreaciones.

## Archivos

Nuevos:

- `ui/contracts/ContractPresentation.kt`: relaciones, búsqueda, filtros, validación, campos editables y payload.
- `ui/contracts/ContractsController.kt`: listado, tarjetas, detalle, filtros y formularios.
- `res/drawable/ic_contract_add.xml`, `ic_contract_calendar.xml`; `res/values/contracts.xml`.
- `ContractPresentationTest.kt`, `ContractsContractInstrumentation.kt`, `ContractsSmokeInstrumentation.kt`.
- `scripts/contracts-validation.init.gradle`: selecciona la clave debug local compatible con la app instalada y el runner de prueba. No contiene claves ni contraseñas y no cambia applicationId/endpoints.
- Este documento y capturas sintéticas en `docs/verification/contracts/`.

Modificados:

- `MainActivity.kt`: navegación y observadores del módulo especializado, disposición de sus sheets.
- `MainViewModel.kt`: estado de listado, carga de catálogos por Repository y estado de guardado.
- `SoftlutionicRepository.kt`: control de permisos de escritura de Contratos y conservación de escrituras confirmadas si el refresco falla.
- `AuthorizationPolicy.kt`: mensaje específico de denegación para Contratos; sin cambios en reglas o clasificación de roles.

Se mantienen Retrofit, Room, SessionManager/JWT, RecordAdapter y RecordMapper existentes. No se creó otra API ni otro Repository.

## Validación

55 tests unitarios, incluyendo 10 de Contratos: resolución de relaciones, respaldo embebido, búsqueda, filtros, IDs numéricos, exclusión de id/read-only, tipo opcional, booleano predeterminado, fechas, permisos por schema para los tres roles y errores de campo.

Comandos Gradle con `ANDROID_HOME` apuntando al SDK local y `-I scripts/contracts-validation.init.gradle` para conservar la firma existente:

```powershell
.\gradlew.bat test assembleDebug installDebug installDebugAndroidTest -I scripts/contracts-validation.init.gradle '-PcontractsRunner=com.softlutionic.mobile.ContractsSmokeInstrumentation'
adb shell am instrument -w com.softlutionic.mobile.test/com.softlutionic.mobile.ContractsSmokeInstrumentation
git diff --check
```

La prueba visual utiliza registros sintéticos con nombres y descripciones largos; no envía POST/PATCH/DELETE al servidor. Las consultas autenticadas reales validan lectura/schema, no prueban mutaciones reales en Azure.

Resultados: 55 tests aprobados, cero fallos/errores; `assembleDebug` e instalación en Pixel_9 correctos conservando datos/sesión con la clave debug compatible. Instrumentación visual `INSTRUMENTATION_CODE: -1`, con listado, búsqueda, filtros, detalle, validación del formulario vacío, precarga de edición y acciones ocultas al denegar escritura en schema. Capturas adicionales: ancho de 320 dp con fuente al 130 % y formulario con teclado abierto; Guardar/Cancelar siguen accesibles mediante desplazamiento. `git diff --check` correcto.
