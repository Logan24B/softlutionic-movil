# Nueva factura: tarifa registrada, modalidad y confirmación

Corrección vigente en Android. Complementa el flujo Cliente + Tipo y reloj de `20_CORRECCION_FACTURAS_CLIENTE_TIPO_RELOJ.md`; sustituye las referencias anteriores al precio editable.

## Contrato API comprobado

InvoicesContractInstrumentation consultó con sesión autenticada GET schema, GET facturas, GET tipos-contrato y OPTIONS facturas. Terminó con código -1. No envió POST, PATCH ni DELETE financieros reales. Evidencia resumida, sin datos personales: `verification/invoices-pricing/api-contract.json`.

- Schema de facturas: ContratoId, Fecha_Emision, Hora_Emision, CodigoFact.
- Respuesta de factura: fechas/horas, CodigoFact, ContratoId, EstadoId, Monto_Total, detalles y relaciones. No hay TipoFactura, Tipo_Factura, tipo_factura ni observación persistible.
- OPTIONS describe PrecioVenta como entero opcional (`required=false`). Se mantiene el contrato existente `lineas: [{ServicioId, Cantidad, PrecioVenta?}]`: omitir PrecioVenta toma la tarifa del servicio.
- TipoContrato sólo contiene id, Nombre y Descripcion. No tiene plazo, duración, días de crédito, condición ni modalidad estructurada.
- Catálogo real: Servicio mensual, Servicio unico, Soporte tecnico.

## Precio y cálculos

Las líneas nuevas muestran Servicios.Precio como texto de solo lectura, con la leyenda “Tarifa registrada del servicio”. No existe un TextInput de precio. Los cambios de cantidad, altas y bajas recalculan subtotal y total usando BigDecimal.

Para una línea nueva se omite PrecioVenta; el backend aplica y conserva su tarifa vigente al crear el detalle. La tarifa del catálogo sirve como estimación visual y puede diferir si el servicio cambió en el servidor. No se envía Monto_Total calculado localmente: el total confirmado es siempre el de la API.

Al editar una factura se conserva y se envía el PrecioVenta histórico de sus detalles; también es de solo lectura. No se sustituye por el precio actual del catálogo. Una línea nueva no puede utilizar una cadena de precio manual para alterar el cálculo o el payload.

Subtotal estimado = cantidad × tarifa aplicable; total estimado = suma de subtotales. No se agregan IVA, descuento ni recargos. El fixture de interfaz mantiene deliberadamente un total confirmado diferente para verificar la autoridad del servidor; ese valor simulado no representa una regla comercial.

La consulta real verificó tres facturas existentes: en las tres, Monto_Total coincidió con la suma de PrecioVenta × Cantidad (`totals_match_line_sums=true`).

## Tipo de factura y observaciones

Tipo de factura es información derivada exclusivamente para UI, nunca un campo enviado. Correspondencia exacta con los nombres reales, normalizando espacios externos, mayúsculas y acentos:

| Tipo de contrato | Tipo de factura visual |
| --- | --- |
| Servicio mensual | CRÉDITO |
| Servicio unico | CONTADO |
| Soporte tecnico | CONTADO |
| Otro nombre no reconocido | No determinada |

La correspondencia refleja la condición mensual de 30 días y las modalidades actuales con vencimiento el mismo día. No se buscan fragmentos como “mensual” dentro de nombres desconocidos, no se infiere desde prosa en Descripcion y no se añaden atributos de backend.

**Observaciones no está persistido por la API móvil actual.** No se añadió un control que prometa guardarlo ni una propiedad JSON inventada.

## Diagnóstico del mensaje rojo

Antes, saveInvoice capturaba cualquier IOException de toda la operación con el mismo texto “No se recibió confirmación…”. La operación incluía resolución/creación del contrato, POST de factura, conversión de respuesta, caché y refresh. No se distinguía la etapa. El Repository ya toleraba IOException y ciertos HTTP del refresh, pero un fallo de caché después del POST seguía propagándose. Por tanto el texto no demostraba que el POST hubiera fallado ni que hubiera llegado a enviarse.

El logcat disponible no conservaba una excepción del intento histórico reportado. La primera consulta de diagnóstico en vivo sufrió SocketTimeoutException; una consulta posterior sí completó schema/GET/OPTIONS. Esto demuestra un timeout observado durante el diagnóstico, no demuestra la causa del POST anterior. No existe evidencia para afirmar su HTTP ni si aquel intento creó una factura. No se creó una factura real sólo para inventar esa conclusión.

Se agregó un método Retrofit que llama al mismo POST `/api/mobile/v1/facturas/` y devuelve Response<JsonObject>, conservando el HTTP. Se verifica 200/201 y una factura válida, con id y código correspondientes. Los errores 400/422 conservan validación, 403 muestra el mensaje de permiso y 409 conserva el conflicto. Un 2xx con cuerpo inválido o una excepción durante el envío/conversión se trata como resultado incierto.

Una confirmación válida no se convierte en fallo por problemas posteriores de caché/refresh. La respuesta se muestra y se cierra el formulario. Si no pudo sincronizarse el listado, se informa “Factura generada. No se pudo actualizar el listado automáticamente. Pulsa Actualizar.”

## Protección ante duplicados

1. Se bloquea el doble toque y se serializan los envíos.
2. Antes de enviar se consulta la colección existente para detectar CodigoFact usado.
3. Antes del POST se persiste el payload pendiente en la caché Room existente; no se cambian entidades ni migraciones.
4. Después de un resultado incierto se consulta GET facturas. Sólo se recupera una coincidencia inequívoca por código que además corresponda al contrato y emisión pendientes.
5. Si aparece la factura, se confirma y muestra sin repetir el POST.
6. Si no puede verificarse la colección, se mantiene pendiente y se bloquea el reenvío.
7. Si la consulta confirma ausencia, se habilita “Reintentar generación”. Ese nuevo toque es autorización explícita; vuelve a consultar antes de enviar. No hay reenvío automático.
8. Se conserva el payload original de un envío pendiente para reconciliarlo, incluyendo emisión. Cambiar código, cliente, tipo o líneas no permite reenviar silenciosamente otra operación. Tras reiniciar, se exige comprobar antes de habilitar reintento.

La comprobación usa el endpoint existente de colección, sin inventar filtro ni ruta. No equivale a una clave de idempotencia del servidor: otro cliente concurrente o una escritura que aparezca después de la comprobación de ausencia siguen siendo límites del contrato actual. No se promete una garantía global que Android no puede proporcionar.

La preparación del contrato sigue siendo una operación separada de la creación atómica de factura + lineas, conforme al flujo anterior. Un fallo antes del POST de factura ahora indica que no se envió la factura; no atribuye incertidumbre al POST inexistente.

## Reloj y resumen

La primera emisión se captura sincrónicamente al generar, antes de suspender por Room o red, usando un solo instante y ZoneId.systemDefault(). Se conservan validaciones de fecha/hora y zona automáticas, selección de cliente/tipo, vencimiento y resolución interna de contrato. Un reintento de un envío pendiente conserva el payload original para poder identificarlo.

Resumen: cantidad de servicios/unidades, modalidad derivada, total estimado, emisión, vencimiento, Generar/Reintentar y Cancelar. No se muestran IDs técnicos ni contratos históricos como selector.

## Payload exacto de ejemplo: factura nueva

```json
{
  "ContratoId": 991,
  "CodigoFact": 777002,
  "Fecha_Emision": "2026-10-29",
  "Hora_Emision": "00:05:00",
  "lineas": [
    {"ServicioId": 904, "Cantidad": 2},
    {"ServicioId": 905, "Cantidad": 1}
  ]
}
```

PrecioVenta se omite en líneas nuevas conforme al contrato. Al editar una línea histórica, su objeto incluye además el PrecioVenta confirmado original, por ejemplo `{"ServicioId":904,"Cantidad":2,"PrecioVenta":850}`. TipoFactura, Observaciones, Monto_Total, EstadoId y UsuarioId no se envían.

## Archivos de esta corrección

- `data/remote/Api.kt`: respuesta HTTP del mismo POST de facturas.
- `data/remote/Network.kt`: diagnóstico del transporte en DEBUG; sin JWT, contraseñas ni cuerpos.
- `data/repository/InvoiceSubmission.kt`: confirmación, estado pendiente, reconciliación y reintento explícito.
- `data/repository/SoftlutionicRepository.kt`: coordinación y persistencia en caché existente.
- `viewmodel/MainViewModel.kt`: captura inmediata, etapas, resultados y errores.
- `ui/invoices/InvoicePresentation.kt`: tarifa protegida y modalidad derivada.
- `ui/invoices/InvoiceSheets.kt`: precio de solo lectura, resumen y botón de reintento.
- `InvoicePresentationTest.kt`, `InvoiceSubmissionTest.kt`, `InvoiceTransportTest.kt`: validación unitaria y Retrofit/Gson con HTTP interceptado.
- `InvoicesSmokeInstrumentation.kt`, `InvoicesContractInstrumentation.kt`: interfaz aislada e inspección autenticada GET/OPTIONS.

Los logs DEBUG incluyen inicio de POST, CodigoFact, HTTP, presencia de body, resultado de deserialización, reconciliación y refresh. No contienen tokens ni datos personales. No se cambia autenticación, endpoints, esquema Room, backend, web ni Azure SQL. Sin git add, commit o push.

## Validación

111 tests sin fallos. Incluyen HTTP 200/201, 400/403/409/422, fallo de refresh y caché después de 201, timeout con y sin commit, respuesta malformada tras commit, código duplicado/ambiguo, reintento explícito y persistencia del bloqueo al recrear el coordinador. No se repite el POST automáticamente.

Test, assembleDebug, installDebug e instalación del APK de pruebas completados con firma compatible en Pixel 9. InvoicesSmokeInstrumentation terminó con -1; InvoicesContractInstrumentation también terminó con -1. Capturas en `verification/invoices-pricing/`. El 201 de creación se probó con Retrofit/OkHttp interceptado y fixture aislado, no es el HTTP de un POST financiero real del usuario.
