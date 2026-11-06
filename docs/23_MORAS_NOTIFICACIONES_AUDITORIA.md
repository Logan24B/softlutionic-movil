# Moras, Notificaciones y Auditoría en Android

Trabajo exclusivo en `C:/proyectos/SoftlutionicAndroid`. No se modificaron Django, Azure, endpoints, modelos ni web. No se hizo commit ni push. Se preservaron los cambios anteriores, incluido Pagos.

## Inspección y evidencia

Se consultaron por GET autenticado schema, moras, notificaciones, auditoria y las relaciones facturas, clientes y usuarios. Resultado en `verification/consultation/contract.txt`: 0 moras, 0 notificaciones y 0 eventos de auditoría; 5 facturas, 22 clientes y 3 usuarios. No se exportaron nombres de clientes, eventos ni credenciales. La consulta terminó con código -1.

Se inspeccionaron en modo lectura los modelos, serializers y ViewSets existentes de `apps/transaccion/{mora,notificaciones,auditoria}`, el registro/política móvil, `factura/services.py` y el esquema SQL de `C:/proyectos/Softlutionic-Mobile-Work`. No se alteraron esos archivos. El schema confirma campos editables y capacidades; los campos enriquecidos y los campos de Auditoría (colección vacía y schema.fields vacío) se describen a partir de ese código disponible. No se inventaron registros para verificar Azure.

## Moras

Campos reales: `id`, `Fecha_Inicio`, `Hora_Inicio`, `Fecha_Final`, `Hora_Final`, `Monto_Mora` (decimal 18,2), `EstadoMora` (boolean), `FacturaId` (OneToOne), `DescripcionMora`. El serializer añade `factura_detalle` y `estado_descripcion`; id y campos enriquecidos son de solo lectura. El schema publica los campos del modelo excepto id; sus capacidades son read=true y create/update/delete=false para la cuenta consultada.

Una mora representa el recargo asociado a una factura. En el código inspeccionado, `sincronizar_mora_factura` la genera/actualiza para contratos mensuales atrasados y la vincula a la confirmación del pago. Android no reproduce esa fórmula, no genera moras, no suma el principal al recargo, no calcula días ni inventa saldo restante.

La tarjeta muestra código comercial de factura, nombre del cliente, estado oficial de factura, vencimiento de factura, estado de la mora y Monto_Mora. El detalle añade inicio, final del período registrado y descripción. FacturaId se resuelve con el catálogo compartido de Facturas o factura_detalle; el cliente se resuelve con las relaciones de Facturas/Contratos/Clientes. Si faltan relaciones, se usa un texto humano de dato no disponible, nunca la PK.

El resumen cuenta facturas distintas con EstadoMora=false y suma exactamente sus Monto_Mora con BigDecimal. Se limita a los registros cargados que coinciden con búsqueda/filtro, se presenta como monto pendiente de mora y no se persiste. Si falta algún monto, no se sustituye por cero: se muestra No disponible. Las moras pagadas no inflan el pendiente.

Búsqueda local por CodigoFact y cliente, sin IDs. Los chips de estado se obtienen de los estados efectivamente presentes en los registros. No hay Crear, Editar ni Eliminar. El Repository también rechaza esas mutaciones para evitar un acceso accidental por el CRUD compartido.

Vacío: “No hay facturas en mora” / “Actualmente no existen obligaciones vencidas.” El módulo se conserva porque el contrato proporciona información financiera útil aunque hoy no haya registros.

## Notificaciones

Campos reales: `id`, `ClienteId`, `Fecha_Envio` (datetime), `Mensaje` (máximo 250), `EstadoNotificacion` (boolean, false por defecto). El serializer añade `cliente_detalle` y `estado_descripcion` de solo lectura. La descripción del modelo es Leida/No leida. No existen título, tipo, referencia de Factura/Pago, hora separada ni UsuarioId.

El schema de la cuenta consultada permite read/create/update/delete. Esto no demuestra un flujo empresarial de creación manual. No se encontró un generador de eventos/notificaciones en el código inspeccionado; no se verificaron automatizaciones externas o triggers de la base desplegada. La colección vacía tampoco permite identificar quién originó registros históricos. Por ello la UI omite Crear, Edición genérica y Eliminar. No se generan ni persisten avisos falsos, ni se infieren tipos a partir del mensaje.

El centro muestra exclusivamente avisos persistidos: mensaje, cliente, fecha/hora y lectura cuando el boolean real está presente. El encabezado fijo “Aviso al cliente” es una etiqueta de presentación, no un título almacenado. No se calculan alertas derivadas de vencimiento en esta versión.

Hay búsqueda por mensaje/cliente y chips basados en los estados de lectura presentes. En detalle puede marcarse como leída solo si schema.permissions.update y EstadoNotificacion editable lo permiten. El Repository vuelve a consultar schema y notificaciones; envía únicamente `PATCH {"EstadoNotificacion": true}` al endpoint existente. Si ya está leída, no repite PATCH. No se muestran controles de lectura cuando falta el campo. No se cambia el mensaje, cliente o fecha para marcar lectura.

El ViewModel bloquea doble toque, necesita conexión y no aplica un estado leído optimista offline. La respuesta confirmada se conserva en Room; un fallo del refresco posterior no invalida esa confirmación. Un resultado incierto requiere actualizar para comprobarlo, sin reintento automático.

Vacío: “Sin notificaciones” / “No tienes avisos pendientes.” Se conserva el módulo para consultar los avisos reales y su lectura cuando existan.

## Auditoría

Campos reales del código existente: `IdLog` (PK), `Tabla`, `Accion`, `ClavePrincipal` (nullable), `Usuario` (texto), `Host`, `Fecha` (datetime), `Detalle` (nullable, máximo 4000). Todos son de solo lectura en el serializer. El ViewSet es ReadOnlyModelViewSet. El schema actual confirma read=true/create=false/update=false/delete=false y fields vacío.

La colección remota está vacía: no se observaron acciones ni entidades reales registradas hoy. Accion y Tabla son textos del registro; no se inventaron categorías ni se afirma que toda operación de la aplicación genere un evento. En el SQL disponible existen defaults SYSTEM_USER/HOST_NAME para usuario/host, pero no se verificaron triggers o procesos de auditoría del despliegue.

Se muestra exactamente Usuario registrado; puede ser un principal técnico SQL y no necesariamente el usuario humano autenticado en Android. Nunca se sustituye por la sesión actual ni se infiere que sea admin. La tarjeta presenta usuario, acción, entidad, referencia humana cuando puede resolverse, descripción no técnica y fecha/hora. Se conserva el valor de Accion sin inventar una acción de negocio más específica.

Cuando Tabla y ClavePrincipal corresponden a un catálogo autorizado cargado, se resuelve Factura a CodigoFact, Pago a NumeroRecibo, Cliente a su nombre y otras entidades soportadas a su referencia humana. No se hacen consultas a Usuarios sin permiso. Si no hay una resolución fiable, la PK no se coloca en la tarjeta.

El detalle presenta trazabilidad de solo lectura. IdLog, ClavePrincipal, Host y descripciones que contienen claves técnicas se conservan en el bloque plegable “Información técnica”, sin protagonismo en la tarjeta. No se elimina el dato original.

Búsqueda por usuario, acción, entidad, referencia humana y descripción pública. Un Bottom Sheet permite usuario/acción/entidad con opciones obtenidas de los eventos cargados y rango de fecha. El rango utiliza el día en la zona del dispositivo, coherente con la fecha/hora mostrada. No se inventan valores cuando no hay eventos. La descripción técnica queda consultable en el bloque técnico; no se usa para búsqueda principal por PK.

Se exige schema.read AND la condición administrativa existente de AuthorizationPolicy (la misma comprobación administrativa ya utilizada para Usuarios). No se añadieron roles, no se cambiaron las decisiones existentes de Superusuario/Administrador/Empleado ni la política central. Coincide con ADMIN_ONLY={'usuarios','auditoria'} inspeccionado en el registro del backend. La navegación, el controller y las lecturas de caché de Auditoría aplican esta restricción; la observación de Room no devuelve eventos a un empleado o sesión no autenticada. El backend sigue siendo la autoridad de acceso remoto. Nunca hay acciones de escritura; Repository también las rechaza.

Vacío: “Sin actividad registrada” / “Los eventos del sistema aparecerán aquí.” Se conserva el módulo solo para administrativos autorizados.

## Diseño, arquitectura y offline

Las tres pantallas tienen tarjetas oscuras navy, cian y violeta, búsqueda y vacíos propios. Comparten una envoltura visual, pero cada módulo construye sus datos y tarjetas especializados: no se imprime un listado genérico campo:valor ni se crean formularios CRUD. Las hojas reutilizan el tema oscuro y NestedScrollView. Refresh permanece en la app bar.

Se mantiene View → MainViewModel → SoftlutionicRepository → Retrofit/Room. Api.kt y los modelos remotos no cambiaron. Se reutiliza la caché y se cargan solo catálogos autorizados; se limpian estados y referencias al cerrar sesión. Room puede mostrar los últimos registros conocidos. No se generan eventos ni moras offline. El estado de sincronización existente y el aviso de relaciones incompletas permiten distinguir datos guardados.

## Archivos de esta tarea

Modificados:
- `app/src/main/java/com/softlutionic/mobile/MainActivity.kt`
- `app/src/main/java/com/softlutionic/mobile/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/softlutionic/mobile/data/repository/SoftlutionicRepository.kt`

Creados:
- `app/src/main/java/com/softlutionic/mobile/ui/consultation/ConsultationController.kt`
- `app/src/main/java/com/softlutionic/mobile/ui/consultation/ConsultationPresentation.kt`
- `app/src/test/java/com/softlutionic/mobile/ConsultationPresentationTest.kt`
- `app/src/androidTest/java/com/softlutionic/mobile/ConsultationContractInstrumentation.kt`
- `app/src/androidTest/java/com/softlutionic/mobile/ConsultationSmokeInstrumentation.kt`
- `scripts/consultation-validation.init.gradle`
- Este documento y `docs/verification/consultation/`.

## Validación

Las pruebas nuevas cubren consulta de Moras/Auditoría, relaciones humanas, ocultamiento de PK, búsqueda/filtro, resumen financiero preciso, datos faltantes, mensaje/cliente/lectura reales, schema/read-only, acceso administrativo combinado con schema, usuario real registrado y resolución/ocultamiento de claves de Auditoría.

La instrumentación usa API simulada y Room en memoria para verificar ausencia de POST/DELETE, rechazo de escritura en Moras/Auditoría, PATCH exclusivo de lectura de notificación sin doble envío, relaciones sin IDs principales, los tres vacíos, caché offline y denegación de caché de Auditoría a empleados y a administradores cuando schema.read=false. No crea avisos, moras ni eventos reales en Azure. Las capturas contienen datos artificiales.

Comandos y resultados finales: `verification/consultation/results.txt`. Se aplica la skill de validación Android existente. El init script selecciona el runner con `-PconsultationRunner=...` y usa la firma local compatible con Pixel_9, conservando sus datos.

Resultado final: 147 pruebas unitarias sin fallos (18 nuevas), test/assembleDebug/assembleDebugAndroidTest/installDebug correctos, ambas instrumentaciones con código -1 y git diff --check sin errores. Capturas finales revisadas de tarjetas, vacíos, detalle y filtros de Auditoría. No se comprobó fuente ampliada ni ancho reducido.

Límites: las colecciones reales están vacías; la representación de filas no vacías y las mutaciones se verifican con fixtures. No se verificaron generadores externos de avisos/auditoría, escrituras de producción, todos los roles en sesiones remotas independientes ni concurrencia entre dispositivos.
