# IDs técnicos fuera de la interfaz Android

Trabajo exclusivo en `C:\proyectos\SoftlutionicAndroid`. Sin modificar backend, Azure, endpoints, modelos, PKs de Room, autenticación ni autorización. Sin commit ni push.

## Revisión por módulo

| Módulo | Hallazgo y resultado |
| --- | --- |
| Clientes | Eliminado el ID del detalle especializado. Departamento sigue mostrándose por nombre; se conserva cédula porque es un documento útil, no una PK. |
| Servicios | Eliminados ID y sección vacía de sistema del detalle especializado. |
| Contratos | Eliminados Contrato #PK de tarjetas/detalle y la sección ID. Se conservan cliente, tipo, descripción, fechas y estado. La búsqueda ya no inventa un número empresarial a partir del ID. |
| Tipos de contrato | Ya mostraba solo Nombre/Descripcion. Revisado; se conserva su ID interno y su uso como catálogo de Contratos. |
| Usuarios | Ya mostraba nombre, usuario, correo, rol y estado sin PK. Revisado; comparación interna de identidad y permisos intactos. |
| Facturas | Renderer genérico excluye PK/FKs; conserva CodigoFact y detalles de líneas con nombres de servicios. |
| Pagos | Renderer genérico excluye PK/FKs; conserva NumeroRecibo y resuelve factura a referencia empresarial cuando está disponible. |
| Moras | Renderer genérico oculta PK/FKs y resuelve factura cuando hay detalle/caché. |
| Notificaciones | Renderer genérico oculta PK/FKs y muestra cliente/usuario legible si está disponible. |
| Auditoría | IdLog y otras claves técnicas se ocultan del texto. Tabla/Accion permanecen. No existía una necesidad funcional definida para una sección avanzada con PK, por lo que no se añadió. |
| Estados de factura y otros módulos genéricos | Se aplica la misma regla; estado/nombre y datos de negocio permanecen visibles. Departamentos permanece como catálogo interno según navegación existente. |
| Dashboard | Revisado: usa CodigoFact y etiquetas remotas de gráficos, sin transformar PK en referencias de negocio; no se modificó. |

## Regla central

`ui/RecordPresentation.kt` contiene `isTechnicalIdField`, las etiquetas humanas de relaciones y el mapeo a claves de detalle embebido. Reconoce id/Id/ID, pk, sufijos Id/ID/_id y claves técnicas prefijadas como IdLog. Preserva explícitamente referencias comerciales y evita confundir palabras como Valid/Grid con un sufijo técnico.

`RecordMapper` deja intacto el JsonObject de origen y `Record.id`. Filtra las claves únicamente al construir title/detail. Nunca usa “Registro PK”, “Servicio PK” o “Factura #PK” como fallback; si faltan datos humanos usa Registro/No disponible. Los detalles de líneas conservan cantidad/precio/subtotal y resuelven servicio por nombre.

Los nombres humanos se obtienen de objetos `*_detalle`, relaciones del schema o catálogos de Room. MainViewModel carga caché legible y solo solicita un catálogo relacionado directo cuando no hay datos guardados y el schema permite leerlo. El mapper también admite relaciones con nombres no convencionales provenientes del schema. Las relaciones cacheadas permiten seguir presentando nombres sin conexión; si no se resuelven, no se muestra la FK como sustituto.

Los selectores genéricos muestran Cliente/Departamento/Servicio/etc., no ClienteId/DepartamentoId/ServicioId. Sus opciones usan el mismo mapper; sus lectores siguen devolviendo el ID del objeto seleccionado. Los campos técnicos sin relación y los read-only no se piden manualmente.

## Identificadores empresariales conservados

La auditoría de modelos incluida en este mismo proyecto Android documenta CodigoFact y NumeroRecibo como campos únicos de negocio distintos de la PK. Ambos siguen visibles, también en Dashboard. La regla no oculta nombres como NumeroFactura, CodigoFactura, NumeroContrato, CodigoContrato, Referencia o ReferenciaPago si aparecen en datos reales. Esto no crea campos nuevos ni números ficticios. Cédula se conserva.

No se censuran números arbitrarios dentro de texto libre o valores de negocio; se filtran las claves técnicas estructuradas y los textos generados por la app a partir de PKs.

## Archivos

- Nuevo `ui/RecordPresentation.kt`.
- Modificados `data/mapper/RecordMapper.kt`, `MainActivity.kt` y `MainViewModel.kt`: mapeo, catálogos de presentación y etiquetas de selectores.
- Modificados `ui/clients/ClientSheets.kt`, `ui/services/ServiceSheets.kt`, `ui/contracts/ContractsController.kt` y `ContractPresentation.kt`: detalles/tarjetas sin IDs y campos editables basados en schema.
- Actualizado `RecordMapperTest.kt`; nuevo `TechnicalIdInstrumentation.kt`.
- Este documento y capturas sintéticas en `docs/verification/technical-ids/`.

No se modificaron Api, SessionManager, Repository, entidades Room, modelos remotos ni AuthorizationPolicy en esta tarea.

## Validación

70 tests unitarios, incluidos siete nuevos sobre claves técnicas, relaciones embebidas/cacheadas, números empresariales, documentos de identidad, fallback de servicios y conservación del JsonObject/PK. Las pruebas previas de payloads de Contratos siguen comprobando que ClienteId/TipoContratoId se envían como números.

`TechnicalIdInstrumentation` usa preferencias aisladas, Room en memoria y Api simulada: create recibe las FKs originales; update/delete reciben la PK original; los registros cacheados conservan IDs mientras la presentación los omite. No hace escrituras HTTP reales. También revisa diálogos genéricos y detalles especializados con datos sintéticos en Pixel 9.

```powershell
.\gradlew.bat test assembleDebug installDebug installDebugAndroidTest -I scripts/contracts-validation.init.gradle '-PcontractsRunner=com.softlutionic.mobile.TechnicalIdInstrumentation'
adb shell am instrument -w com.softlutionic.mobile.test/com.softlutionic.mobile.TechnicalIdInstrumentation
git diff --check
```

Se reutiliza la firma debug local compatible para preservar datos y sesión de la app instalada. No se declara validación de mutaciones reales en Azure.

Resultado final: 70 tests aprobados, cero fallos/errores; assembleDebug e installDebug correctos en Pixel_9. Instrumentación `INSTRUMENTATION_CODE: -1`: claves de create/update/delete conservadas y detalles genéricos/especializados sin IDs. Se revisaron capturas de Clientes, Servicios, Contratos y Facturas; el diálogo genérico conserva fondo navy y referencias comerciales. `git diff --check` correcto.
