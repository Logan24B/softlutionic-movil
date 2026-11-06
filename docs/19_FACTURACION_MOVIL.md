# Facturación especializada Android

> Flujo anterior. La corrección vigente de Cliente + Tipo y emisión automática se describe en [20_CORRECCION_FACTURAS_CLIENTE_TIPO_RELOJ.md](20_CORRECCION_FACTURAS_CLIENTE_TIPO_RELOJ.md).

Trabajo exclusivo en `C:\proyectos\SoftlutionicAndroid`. Sin cambios en Django, Azure, endpoints, modelos, entidades Room ni web; sin commit ni push.

## Contrato comprobado

`InvoicesContractInstrumentation` consultó el schema móvil, GET facturas, GET estados-factura y OPTIONS facturas con la sesión existente del Pixel 9. Se revisaron también `docs/03_API_MOVIL.md`, la auditoría de modelos incluida en Android y el formulario móvil anterior. No se hicieron escrituras financieras reales.

Los códigos visibles `1002`, `2` y `1001` corresponden al campo empresarial **CodigoFact**, no a la PK. No se genera una secuencia local.

| Campo Factura | Contrato móvil actual | Interfaz |
| --- | --- | --- |
| id | PK técnica, read-only | Nunca visible ni solicitada |
| ContratoId | Relación requerida con contratos | Selector humano; payload conserva ID |
| CodigoFact | IntegerField requerido, único según auditoría del modelo | Código de factura ingresado por usuario; sin secuencia inventada |
| Fecha_Emision | DateField requerido | Material DatePicker; presentación legible, envío ISO |
| Hora_Emision | TimeField requerido | Material TimePicker; presentación legible, envío HH:mm:ss |
| Fecha_Vencimiento | Read-only en OPTIONS | Se muestra solo desde respuesta; no se calcula localmente |
| Hora_Vencimiento | Read-only en OPTIONS | Se muestra solo desde respuesta |
| Monto_Total | Decimal read-only, 18 dígitos/2 decimales | Total confirmado recibido; nunca se envía |
| UsuarioId | Asignado por API móvil/JWT; ausente de fields editables | Sin selector ni payload manual; usuario_detalle permite mostrar el nombre |
| EstadoId | Omitido del schema móvil editable; respuesta contiene estado_detalle | Sin selector al crear; se utiliza el estado devuelto |

Schema actual de Facturas: read=true, create=true, update=true, delete=false para el usuario consultado. La app consulta las capacidades del usuario actual y su política local; no generaliza esos permisos a todos los usuarios.

Detalle real: id, FacturaId, ServicioId, Cantidad y PrecioVenta. GET también incluye servicio_detalle y Subtotal calculado/read-only. Cantidad y PrecioVenta son enteros; precio es opcional. La auditoría y el formulario existente confirman que omitir PrecioVenta utiliza la tarifa del servicio. La UI exige cantidad >0 y precio entero no negativo. No se incluyen FacturaId ni id de línea en el payload.

Estados reales consultados: Pendiente, Pagada, Vencida y Anulada. Sus textos proceden del catálogo/respuesta; los colores de badge no crean nuevos estados.

## Flujo y diseño

1. Listado especializado con CodigoFact destacado, badge de estado, cliente, tipo/contexto del contrato, Monto_Total confirmado y fecha de emisión legible. Sin IDs técnicos.
2. Búsqueda local por código comercial/cliente/contrato y filtros por estado, cliente y rango de emisión. Sin llamadas por carácter.
3. Botones independientes Nueva factura/Actualizar, 63/37 %, 52 dp de alto, 10 dp de separación y esquinas de 14 dp. Violeta sólido con texto blanco y acción secundaria navy con borde cian.
4. Formulario oscuro grande y desplazable, con información de factura, contexto del cliente, servicios y resumen. El borrador permanece en ViewModel durante recreaciones, sin presentarse como factura confirmada.
5. Contrato muestra cliente + tipo + descripción disponible. Se resuelve por catálogos del Repository y objetos contrato_detalle; el cliente procede de ClienteId/cliente_detalle del contrato. No se solicita ClienteId de nuevo.
6. Servicios se eligen en otro Bottom Sheet con búsqueda, nombre, descripción breve y tarifa real. Cada línea tiene tarjeta, stepper/input de cantidad, precio de venta opcional y botón discreto de quitar.
7. Precio vacío muestra la tarifa que se usará y se omite de lineas; precio explícito se envía. Al editar se conserva PrecioVenta de la factura original, sin sustituirlo por la tarifa actual.
8. Subtotal estimado = cantidad × precio efectivo; total estimado = suma de subtotales, mediante BigDecimal, sin impuestos/descuentos/cargos nuevos. Se actualiza al agregar/quitar o cambiar cantidad/precio. Si una línea es inválida no se muestra un total parcial como válido.
9. Generar factura valida encabezado y líneas; spinner, controles bloqueados y estado de guardado evitan doble envío. El formulario cierra tras confirmación y muestra el detalle de la respuesta.
10. Detalle especializado: código, estado, cliente/contrato, emisión/vencimiento legibles, cada servicio con cantidad/precio/subtotal recibidos y TOTAL recibido. Nunca sustituye Monto_Total confirmado por el estimado local.

## Payload exacto y atomicidad

Para una creación con dos servicios, el contrato vigente construye:

```json
{
  "ContratoId": 901,
  "CodigoFact": 777002,
  "Fecha_Emision": "2026-10-28",
  "Hora_Emision": "09:30:00",
  "lineas": [
    {"ServicioId": 904, "Cantidad": 2, "PrecioVenta": 900},
    {"ServicioId": 905, "Cantidad": 1}
  ]
}
```

Los valores anteriores son sintéticos de pruebas; la app utiliza las selecciones reales. No envía id, UsuarioId, EstadoId, Monto_Total, fechas/horas de vencimiento read-only, FacturaId ni `detalles` como entrada.

Se utiliza el mismo `SoftlutionicRepository.save` y `Api.create("facturas", payload)`: un solo POST al endpoint ya existente. No hay POSTs separados de detalles. La API existente recibe `lineas` mediante AtomicInvoiceSerializer y su transacción; esa lógica no se modificó. En edición se usa el PATCH existente y `lineas` representa la colección completa de líneas. No se declara una prueba nueva del rollback real del servidor: se preserva y comprueba el contrato de llamada del cliente.

## Permisos, errores y conexión

- Crear/editar/eliminar dependen de schema AND AuthorizationPolicy. No se agregan restricciones por rol a Facturas. Se mantienen Servicios y Tipos de contrato de solo lectura para Empleado.
- No se renderiza Eliminar cuando delete=false. Si un servidor lo autoriza, se respeta la capacidad con confirmación; Repository vuelve a validar permisos antes de la llamada.
- Usuario/estado/vencimiento/total no se piden manualmente cuando el contrato los asigna/calcula. No se inventan reglas de cierre/pago; las restricciones de edición del servidor se muestran como errores humanos.
- Errores de encabezado y líneas aparecen en el formulario, sin reducirlos a HTTP 400 ni exponer una FK numérica de un error de servicio inexistente.
- Room sigue mostrando datos guardados. Sin red detectada se cancela antes de enviar y se indica “No hay conexión. Conéctate para generar la factura.”
- Si se pierde la respuesta durante una solicitud, no se simula éxito: se pide revisar el listado antes de reenviar. No hay cola de facturas offline ni inserción confirmada antes de recibir la respuesta.
- Una escritura confirmada conserva su respuesta en Room si falla el GET de refresco posterior. La app muestra esa respuesta confirmada; esto evita presentar un fallo de lectura como un fallo de creación.

## Archivos

Nuevos:

- `ui/invoices/InvoicePresentation.kt`: relaciones, filtros, borrador, validación, importes y payload.
- `ui/invoices/InvoiceViews.kt`: componentes Material comunes del módulo.
- `ui/invoices/InvoicesController.kt`: listado, navegación local y consumo de confirmación.
- `ui/invoices/InvoiceSheets.kt`: formulario, selectores, líneas, filtros y detalle.
- `res/drawable/ic_invoice_remove.xml`.
- `res/values/invoices.xml`: tema oscuro del selector de hora; el calendario reutiliza el tema oscuro existente.
- `InvoicePresentationTest.kt`, `InvoicesContractInstrumentation.kt`, `InvoicesSmokeInstrumentation.kt`.
- Este documento y capturas sintéticas en `docs/verification/invoices/`.

Modificados:

- MainActivity: integración del módulo y observadores especializados.
- MainViewModel: catálogos, filtros, borrador, validación, estado de guardado y confirmación de factura.
- SoftlutionicRepository: devuelve la respuesta confirmada del save compartido, valida capacidades de Facturas y preserva confirmación si falla el GET posterior.
- AuthorizationPolicy: únicamente mensaje específico de denegación de Facturas; clasificación/reglas intactas.

Sin nueva API ni nuevo Repository. Api.kt, Room, modelos, JWT y endpoints permanecen iguales. Los demás módulos conservan su presentación y CRUD.

## Validación

86 tests unitarios aprobados, incluidos 16 de Facturas: código/ID, contrato/cliente/estado, servicios, cantidades, precios, subtotales/total, múltiples líneas y eliminación, payload exacto lineas, exclusión de campos generados, edición sin repreciar, permisos, búsqueda/filtros, fechas y errores anidados.

Comandos con SDK local y firma debug compatible:

```powershell
.\gradlew.bat test assembleDebug -I scripts/contracts-validation.init.gradle
.\gradlew.bat installDebug installDebugAndroidTest -I scripts/contracts-validation.init.gradle '-PcontractsRunner=com.softlutionic.mobile.InvoicesSmokeInstrumentation'
adb shell am instrument -w com.softlutionic.mobile.test/com.softlutionic.mobile.InvoicesSmokeInstrumentation
git diff --check
```

La prueba financiera utiliza Api simulada, preferencias aisladas y Room en memoria. Ningún POST/PATCH/DELETE de prueba se envía a Azure ni se inserta una factura sintética en la caché real de la app. La auditoría autenticada verifica schema/lectura/metadata; las mutaciones reales del backend no se declaran validadas.

La respuesta simulada usa deliberadamente un Monto_Total distinto del estimado para comprobar que el detalle presenta la autoridad del servidor, en vez de recalcular y sustituir el dato recibido. Esta variación es una condición de prueba; no agrega cargos ni reglas de cálculo a la aplicación.

La revisión de hojas consecutivas (detalle → edición → detalle) detectó y corrigió un callback de cierre antiguo que podía desconectar la hoja nueva. La prueba ahora cambia cantidades durante edición y comprueba que el total visual se actualiza; los errores de conexión/líneas se anuncian y se desplazan a la vista al concluir la solicitud.

Resultado final: **86 tests aprobados, cero fallos/errores**. `assembleDebug`, `installDebug` e instalación del APK de pruebas correctos en Pixel_9 conservando sesión/datos. Instrumentación final `INSTRUMENTATION_CODE: -1`: un POST de creación confirmada, un PATCH de edición, cero POSTs independientes de detalle, doble envío bloqueado y caché sin nuevas facturas ante fallos de red/validación. Se comprobó también mediante visibilidad real de la vista que el mensaje financiero queda dentro de la pantalla después del fallo. Capturas finales revisadas de listado, filtros, pickers oscuros, formulario con teclado/resumen, edición, detalle y errores. `git diff --check` correcto.
