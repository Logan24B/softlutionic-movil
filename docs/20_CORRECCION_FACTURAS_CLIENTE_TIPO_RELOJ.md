# Corrección de facturas: cliente, tipo y reloj

> La corrección posterior de precio de solo lectura, modalidad derivada y confirmación está en [21_FACTURAS_PRECIOS_CONFIRMACION.md](21_FACTURAS_PRECIOS_CONFIRMACION.md).

Este documento sustituye el flujo de selección de contrato histórico y los selectores manuales de emisión descritos en `19_FACTURACION_MOVIL.md`.

## Comportamiento comprobado

Se consultaron solamente como referencia, sin modificaciones, estos archivos de `C:\proyectos\Softlutionic-Mobile-Work`:

- `frontend/Softlutionic/js/facturas.js`: `calculateDueDate`, `ensureContractForInvoice`, carga de contratos y guardado de factura.
- `apps/transaccion/contratos/models.py` y `contratos/api.py`.
- `apps/transaccion/factura/services.py` y `factura/serializers.py`.

La web busca contratos por ClienteId, TipoContratoId y EstadoContrato=true, ordenados por id ascendente. Reutiliza el primero. No verifica un intervalo de fechas. Si no encuentra uno, crea un contrato con el cliente, tipo, fecha de emisión, vencimiento calculado, estado activo y descripción del tipo (o su nombre). Android reproduce esa resolución con los endpoints existentes y sus permisos efectivos.

TipoContrato tiene Nombre y Descripcion; no existe un campo de plazo. El nombre normalizado exactamente `servicio mensual` añade 30 días; cualquier otro nombre deja la misma fecha. El servicio backend fija Hora_Vencimiento a 23:59:00. La interfaz anticipa esa regla y mantiene el resultado confirmado por el servidor como autoridad.

## Formulario

Cliente y Tipo de contrato son obligatorios. Cliente ofrece búsqueda local por nombre, apellido, cédula y correo, y muestra clientes activos. Tipo ofrece búsqueda por nombre y descripción. No se presenta un selector de contratos históricos ni identificadores técnicos.

La emisión visible es una vista previa que se actualiza mientras el formulario está activo. Al generar se captura una sola vez `ZonedDateTime.now(Clock.system(ZoneId.systemDefault()))`; fecha y hora del payload proceden de ese mismo instante, no del momento de abrir la pantalla. Esa captura ocurre antes de preparar el contrato y enviar la factura. Al editar se conserva la emisión original y se omiten ambos campos del PATCH, como hace la web.

Antes de generar se leen Settings.Global.AUTO_TIME y AUTO_TIME_ZONE y se valida la zona local. Si alguna condición falla se muestra la advertencia con Cancelar y Abrir configuración; esta última abre Settings.ACTION_DATE_SETTINGS. No se cambia el reloj, no se añaden permisos peligrosos y no se depende de hora de red.

Servicios, cantidades, precios, subtotales y total conservan su funcionamiento. El total definitivo y el estado vienen del servidor. La API móvil no respalda campos de IVA, descuento, tipo de factura u observación: no se añadieron. CodigoFact se conserva porque el schema móvil lo requiere.

## Payload de ejemplo exacto

Si no existe contrato activo para la combinación, primero se envía a contratos:

```json
{"ClienteId":902,"TipoContratoId":903,"Fecha_Inc":"2026-10-29","Fecha_Fin":"2026-11-28","EstadoContrato":true,"Descripcion":"Servicio mensual"}
```

Con el contrato confirmado 991, el POST de factura es:

```json
{"ContratoId":991,"CodigoFact":777002,"Fecha_Emision":"2026-10-29","Hora_Emision":"00:05:00","lineas":[{"ServicioId":904,"Cantidad":2,"PrecioVenta":900},{"ServicioId":905,"Cantidad":1}]}
```

Los valores son una muestra de prueba. Descripcion usa la descripción real del tipo si existe; PrecioVenta se omite cuando corresponde usar el precio del servicio. No se envían ClienteId/TipoContratoId directamente en la factura, UsuarioId, EstadoId, monto total ni vencimiento calculado por el servidor. Si ya hay contrato activo, sólo se envía el segundo POST. La factura y sus detalles conservan el envío atómico mediante `lineas`, sin POST independientes de detalles.

La preparación del contrato es una petición separada, igual que en la web: si la factura falla, puede quedar el contrato creado. No se inventó una transacción entre recursos ni una eliminación compensatoria. Reintentar consulta nuevamente y reutiliza el contrato activo.

## Archivos de esta corrección

- `ui/invoices/InvoiceTiming.kt`: reloj, configuración automática y regla de vencimiento.
- `ui/invoices/InvoicePresentation.kt`: selección, validación, combinación activa y payload del contrato.
- `ui/invoices/InvoiceSheets.kt`: selectores, búsqueda, vista previa y advertencia.
- `ui/invoices/InvoicesController.kt`: hidratación de catálogos al editar.
- `viewmodel/MainViewModel.kt`: captura al enviar y coordinación de la resolución.
- `data/repository/SoftlutionicRepository.kt`: búsqueda fresca, reutilización o creación autorizada del contrato.
- `MainActivity.kt`: observación de estados del formulario.
- `InvoicePresentationTest.kt`, `InvoiceTimingTest.kt`, `InvoicesSmokeInstrumentation.kt`: regresiones y flujo aislado.

Se mantienen JWT, rutas Retrofit, modelos y Room. El árbol ya contenía trabajo de tareas anteriores; no se revirtió ni se preparó para commit. Backend y web sólo se leyeron.

## Validación

94 pruebas unitarias sin fallos. Gradle test, assembleDebug e installDebug ejecutados con firma de depuración compatible con la instalación existente. La instrumentación usa API simulada y caché aislada: verifica selección/búsqueda, ambos ajustes automáticos desactivados por separado, cambio de tipo, captura al cruzar medianoche, creación y reutilización del contrato, POST atómico, edición y fallos sin confirmaciones falsas. No acredita escrituras financieras reales en Azure.

Resultado de InvoicesSmokeInstrumentation: código -1 (éxito), prepared_contract_posts=1, confirmed_create_calls=1, independent_detail_posts=0. Capturas en `verification/invoices-corrected/`. El total simulado confirmado de 3010 difiere deliberadamente de la suma local de 3000 para comprobar que la pantalla respeta el total del servidor; no representa una regla de recargo. Las primeras capturas del formulario preceden al ajuste final del separador visual de vencimiento.
