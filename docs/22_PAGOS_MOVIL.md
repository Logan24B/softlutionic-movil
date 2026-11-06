# Pagos: facturas por gestionar e historial

Implementación Android del 28 de octubre de 2026. No se modificaron Django, Azure, endpoints, modelos ni web. No se hizo commit ni push. Se conservaron los cambios locales anteriores.

## Evidencia del contrato y límites

Se leyeron `Api.kt`, `MainActivity`, `MainViewModel`, `SoftlutionicRepository`, `RecordMapper`, `RecordAdapter`, `AuthorizationPolicy` y la implementación especializada de Facturas. El primer intento autenticado devolvió HTTP 401 y PowerShell falló al recibir la conexión. Una consulta posterior desde Pixel_9 sí completó GET schema, facturas, pagos y estados-factura; su resultado está en `verification/payments/contract.txt`. Se encontraron 5 facturas, 3 pagos y 4 estados. No se exportaron datos personales ni tokens.

Como evidencia de lógica, se consultaron sin modificar los archivos existentes de `C:/proyectos/Softlutionic-Mobile-Work`: `apps/transaccion/pagos/{models,serializers,api}.py`, `apps/transaccion/factura/{models,serializers,services}.py`, `apps/mobile_api/{api,registry}.py` y `frontend/Softlutionic/js/pagos.js`. Los campos y permisos relevantes coinciden con el GET autenticado actual; la ejecución financiera del código remoto no se verificó mediante escrituras reales. Android toma los permisos y campos editables del schema que reciba en ejecución.

## Campos reales

Pago contiene `id`, `Fecha_Pago` (DateTime obligatorio, no autogenerado), `Monto_Pagado` (decimal 18,2, obligatorio, no negativo), `EstadoPago` (boolean, default false), `MetodoPago` (boolean, default false), `FacturaId` (relación obligatoria) y `NumeroRecibo` (entero comercial obligatorio y único). El serializer añade `factura_detalle`, `estado_descripcion` y `metodo_descripcion`, de solo lectura, además de `id`. No existe `UsuarioId` en Pago ni un campo Hora separado.

`MetodoPago=false` significa Efectivo; true significa Transferencia. No existe Tarjeta en este contrato. `EstadoPago` significa Confirmado/No confirmado y se distingue del estado de la factura. El código visual REC se forma exclusivamente con `NumeroRecibo`, siguiendo la web; nunca con la PK.

Factura contiene las relaciones `UsuarioId`, `ContratoId`, `EstadoId`, código comercial entero `CodigoFact`, fechas/horas de emisión y vencimiento, `Monto_Total`, relaciones expandidas y detalles. El total, vencimiento y detalles son de solo lectura en su serializer; la API móvil además controla usuario y estado. EstadoFactura contiene `id` y `estado`.

El GET autenticado actual confirmó Pendiente (1), Pagada (2), Vencida (3) y Anulada (4). Esos IDs son evidencia de la consulta, no constantes de la aplicación. La pantalla carga las opciones reales de `estados-factura`, incluidos estados adicionales que devuelva la API.

El schema autenticado de Pagos confirmó Fecha_Pago, Monto_Pagado, FacturaId y NumeroRecibo como requeridos; EstadoPago y MetodoPago son opcionales. Para la cuenta consultada: read/create/update=true, delete=false. El catálogo estados-factura es de lectura; Facturas permite read/create/update y no delete. Los campos enriquecidos/id no aparecen como editables.

## Navegación y consumo interno

`MobileNavigation.hiddenCatalogs` oculta departamentos y estados-factura (incluido el alias estados_factura) exclusivamente al construir los módulos navegables en MainActivity. El mismo conjunto de módulos permitidos protege la navegación directa. No se quitó el módulo del schema, Retrofit, Repository ni Room. Pagos y Facturas siguen consultando el catálogo por sus rutas existentes y usando la misma caché.

## Pantalla y filtros

Pagos tiene búsqueda global, chips de estado, un bloque de facturas pendientes y otro de pagos registrados. Se usan tarjetas y hojas especializadas, sin renderer CRUD genérico ni botón global Nuevo pago. Actualizar sigue en la app bar.

La búsqueda local combina exclusivamente `CodigoFact` y nombre/apellido del cliente, normalizados sin acentos ni diferencias de mayúsculas. No compara PK, FacturaId, ClienteId ni EstadoId. El filtro usa el ID del catálogo internamente y el nombre humano en pantalla. Búsqueda y estado se aplican conjuntamente a ambos bloques. Los datos corresponden a las colecciones cargadas: no se afirma buscar registros ausentes de caché sin conexión.

El cliente se resuelve por `cliente_detalle`, por `contrato_detalle.cliente_detalle` o por los catálogos compartidos. Para el historial, una factura de la colección actual tiene prioridad sobre su snapshot embebido en un pago.

Solo los nombres oficiales normalizados exactamente Pendiente y Vencida habilitan una factura en el primer bloque. Pagada, Anulada y estados desconocidos quedan fuera. Las fechas nunca sustituyen EstadoId. Los badges muestran el nombre recibido: ámbar para pendiente, error para vencida y verde para pagada. Las facturas pagadas se localizan mediante sus pagos en el historial. Los vacíos se adaptan al filtro y la búsqueda. No se muestran IDs técnicos.

## Registrar, editar y actualizar

Registrar pago comienza en una factura pendiente sin pagos asociados. Se muestran factura, cliente, total y estado como contexto. `FacturaId` se selecciona internamente. El monto del registro nuevo es el `Monto_Total` devuelto por la API, siguiendo la acción web `markInvoiceAsPaid`; no se calcula saldo ni mora localmente.

Se captura Número de recibo y método real del contrato. El nuevo registro confirmado envía únicamente los campos admitidos y editables del schema: FacturaId, NumeroRecibo, Monto_Pagado, EstadoPago, MetodoPago y Fecha_Pago. La fecha/hora se captura al pulsar Registrar, en un mismo instante con ZoneId.systemDefault, y se envía como datetime ISO con offset. Se reutiliza DeviceInvoiceTime: exige fecha/hora y zona automáticas. El backend no autogenera Fecha_Pago.

Si la factura ya tiene un pago, la tarjeta conduce al detalle existente. No se crea un segundo pago a ciegas. Con permiso update puede editarse recibo, monto, método, confirmación y fecha/hora según campos editables del schema; FacturaId permanece asociado. La fecha existente se preserva si no se cambia. Los cambios se envían por PATCH al endpoint existente.

Antes de escribir se consultan facturas, estados y pagos frescos. Se revalida el estado oficial al crear y la relación del pago al editar. Se bloquea doble toque en ViewModel, se deshabilitan controles y cierre durante el envío, y no hay reintentos automáticos de la operación financiera. El Repository revalida sesión, schema, permisos efectivos y claves editables del payload, incluso si se usa su método de guardado compartido.

Tras la respuesta confirmada se conserva el pago, se muestra el detalle, y se vuelven a cargar facturas/pagos. Android nunca cambia EstadoId. `sincronizar_estado_factura` del backend marca Pagada cuando existe algún pago confirmado; al desconfirmar recalcula Pendiente/Vencida, y preserva Anulada. Una falla de refresco posterior no convierte una escritura confirmada en error.

No existe un flujo contable de pagos parciales/saldo en ese código: el serializer acepta un monto no negativo, pero cualquier pago confirmado marca la factura Pagada sin comparar sumas. Por eso el registro desde factura usa el total. La edición mantiene el monto editable que permite el contrato; no lo presenta como abono ni saldo restante.

No existe un endpoint dedicado de reversión/anulación. La edición del estado de confirmación usa la capacidad update existente, tal como hace la web. No se añadió una falsa acción Revertir, ni borrado: el registro móvil declara delete=false para pagos. No se cambiaron las reglas de roles; se combinan schema.permissions y AuthorizationPolicy vigente. Solo se añadió una descripción de error específica para Pagos.

## Offline e incertidumbre

Room permite consultar historial y relaciones guardadas. Sin conexión no se inicia un registro y se muestra “Sin conexión. Conéctate para registrar el pago.” No hay confirmación local simulada.

PaymentSubmission guarda en Room la operación antes de enviarla. Si se pierde la respuesta o hay un resultado incierto, la operación permanece pendiente y bloquea nuevas escrituras financieras desde este flujo. Actualizar consulta el servidor y puede reconciliarla comparando campos, recibo e instante; para PATCH exige la misma PK internamente. Esa comprobación no reenvía la petición. Un rechazo definido 400/401/403/404/409/422 libera el intento; un error incierto se conserva.

Si el servidor no devuelve una coincidencia, se requiere revisar el recibo/operación; la aplicación no borra la incertidumbre ni habilita un reenvío ciego. No existe idempotencia distribuida en el contrato inspeccionado: la prevención local no garantiza exclusión frente a otro dispositivo que escriba simultáneamente.

## Archivos de esta tarea

Modificados:
- `app/src/main/java/com/softlutionic/mobile/MainActivity.kt`
- `app/src/main/java/com/softlutionic/mobile/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/softlutionic/mobile/data/repository/SoftlutionicRepository.kt`
- `app/src/main/java/com/softlutionic/mobile/domain/auth/AuthorizationPolicy.kt` (solo mensaje de error)

Creados:
- `app/src/main/java/com/softlutionic/mobile/ui/payments/PaymentPresentation.kt`
- `app/src/main/java/com/softlutionic/mobile/ui/payments/PaymentsController.kt`
- `app/src/main/java/com/softlutionic/mobile/data/repository/PaymentSubmission.kt`
- `app/src/test/java/com/softlutionic/mobile/PaymentPresentationTest.kt`
- `app/src/test/java/com/softlutionic/mobile/PaymentSubmissionTest.kt`
- `app/src/androidTest/java/com/softlutionic/mobile/PaymentsContractInstrumentation.kt`
- `app/src/androidTest/java/com/softlutionic/mobile/PaymentsSmokeInstrumentation.kt`
- `scripts/payments-validation.init.gradle`
- Este documento y las evidencias de validación en `docs/verification/payments/`.

## Validación

Las pruebas unitarias incluyen navegación/catálogo conservado, resolución de estados con IDs distintos de producción, búsqueda comercial y por cliente, exclusión de IDs, filtros combinados, todos los estados, exclusión de Pagada/Anulada, payload interno, campos read-only, recibo/método reales, prioridad de factura fresca, timeout sin reenvío, reconciliación tras reinicio y confirmación preservada ante fallos de caché.

La instrumentación de flujo usa API simulada, sesión de fixture y Room en memoria; comprueba doble toque con un solo POST, actualización de estado desde el servidor simulado, edición/desconfirmación, bloqueo offline sin POST, timeout sin éxito ni reenvío y permisos denegados. Sus capturas no contienen datos de clientes reales. La instrumentación de contrato usa únicamente GET.

Los resultados finales se registran en `docs/verification/payments/results.txt`. Los comandos usan `-I scripts/payments-validation.init.gradle` para conservar la firma compatible con la app instalada en Pixel_9, sin borrar datos. El script permite seleccionar el runner con `-PpaymentsRunner=...`.

Resultado final: 129 pruebas unitarias sin fallos (18 nuevas), test/assembleDebug/assembleDebugAndroidTest/installDebug correctos, ambas instrumentaciones con código -1 y git diff --check sin errores. Se revisaron las capturas finales de listado, formulario y detalle. La comprobación visual permitió corregir el interruptor en Android 17 y aplicar el tema oscuro de hojas existente. No se comprobó fuente ampliada ni ancho reducido.

Pendiente de verificación real: una operación financiera real autorizada por el usuario y la concurrencia entre dispositivos. Schema, catálogo y capacidades de la cuenta consultada sí se verificaron mediante GET autenticado. No se realizó un pago real para probar la interfaz.
