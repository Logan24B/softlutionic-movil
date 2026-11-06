# Servicios: presentación especializada

## Contrato y alcance

Se inspeccionaron en lectura `apps/catalogos/servicios/models.py` y
`serializers.py` del backend de referencia en `C:/proyectos/Softlutionic-Mobile-Work`,
además del schema dinámico y las capas Android existentes. No se modificó Django.
Las consultas anónimas a `/api/mobile/v1/schema/` y `/api/mobile/v1/servicios/`
respondieron HTTP 401. No se obtuvo una muestra autenticada del despliegue actual;
el contrato local no sustituye esa comprobación. En ejecución se usan el schema,
sus campos editables y sus permisos recibidos por la sesión real.

Contrato local verificado:

| Campo | Tipo | Presentación/entrada |
|---|---|---|
| id | Campo automático del modelo | Solo lectura en detalle; nunca se genera ni se envía en el body |
| Nombre_Servicio | CharField, máximo 50 | Nombre del servicio |
| Descripcion | CharField, máximo 200 | Descripción multilínea |
| Precio | IntegerField | Entero no negativo; cero admitido por el serializer |
| Duracion | CharField, máximo 50 | Texto sin conversión ni unidades agregadas |

La entrada respeta `required`, `max_length` y `read_only` del schema. La lista
permitida del payload incluye solamente los cuatro campos del servicio; excluye
siempre id, incluso si un schema incorrecto lo incluyera como editable.

## Experiencia de uso

- **Listado:** encabezado SERVICIOS, subtítulo, búsqueda, selector de orden,
  actualización mediante icono y CTA «+ Nuevo servicio» según permiso create.
  Cards con icono vectorial, nombre, precio destacado, descripción con un máximo
  de tres líneas y ellipsis, y duración completa que puede ocupar varias líneas.
  El precio ajusta su tamaño para mantener el importe completo en pantallas estrechas.
- **Búsqueda:** filtra Nombre_Servicio y Descripcion entre registros cargados;
  ignora mayúsculas y acentos, sin peticiones por tecla. No consulta datos que
  el servidor no haya incluido en la lista recibida.
- **Ordenamiento:** nombre A–Z/Z–A y precio menor/mayor. El precio se compara
  numéricamente, no como texto; los importes ausentes permanecen al final.
  La consulta y el orden viven en StateFlow del ViewModel y se limpian al cerrar sesión.
- **Crear:** Bottom Sheet oscuro expandido con secciones de información del
  servicio y comercial, inputs Material y scroll anidado. Valida requeridos,
  longitud, precio entero no negativo y límites de IntegerField. El teclado
  numérico no ofrece decimales para el IntegerField actual. El precio lleva prefijo C$.
- **Editar:** reutiliza el mismo formulario, precarga los cuatro valores y usa
  PATCH a través del CRUD compartido. ID nunca es un input ni se añade al body.
- **Guardar:** progreso, deshabilitado de controles y guardia de mutación en
  ViewModel para evitar dos operaciones simultáneas de Servicios. Cierra al
  confirmar el servidor y muestra Snackbar. Un error mantiene el formulario.
  Los errores JSON conocidos de campos se muestran con etiquetas legibles;
  no se expone HTML de un proxy ni el mensaje técnico de Retrofit.
- **Detalle:** card con descripción completa, precio y duración; ID en una
  sección secundaria de información del sistema. Editar/eliminar dependen del schema.
- **Eliminar:** confirmación «¿Eliminar servicio?» y advertencia sobre registros
  relacionados. HTTP 409 muestra el mensaje de dependencias; una respuesta de red
  incierta no se presenta como una eliminación confirmada.
- **Offline/vacío:** mantiene Room, el listado almacenado y el banner existente.
  Hay estados de carga, sin coincidencias y sin servicios. Los formularios solicitan
  ajuste de tamaño con el teclado y permiten desplazamiento del contenido.

## Precio y duración

`ServicePresentation.price()` usa BigDecimal y NumberFormat para representar los
importes recibidos sin precisión de coma flotante y sin añadir centavos artificiales:
por ejemplo, un entero 1200 aparece como `C$1,200`. La entrada actual convierte el
precio a JsonPrimitive numérico entero. Solo se admite entrada decimal si el schema
declara explícitamente DecimalField/FloatField; esto no cambia el modelo actual.

Duracion se trata como texto. No se convierte a minutos, no se añaden unidades y
no se deduce una duración numérica. Solo se aplica el recorte normal de espacios
exteriores del formulario, consistente con los campos de texto del serializer.

## Facturación y arquitectura

No se duplicaron API, Repository ni modelo de negocio. Se reutiliza Record, el
CRUD genérico, Retrofit, JWT, Room y los StateFlow existentes. No hay dependencias nuevas.
`repository.options("servicios")`, `prepareForm()` de facturas, sus selectores,
líneas y cálculos de precios permanecen sin cambios. La ordenación del catálogo
solo produce una lista de presentación y no altera el payload original.

El repositorio conserva la respuesta de una mutación de Servicios ya confirmada
si el GET posterior de actualización falla. Así no se induce a repetir un POST
que el servidor ya confirmó. El comportamiento de otros módulos se conserva.
Departamentos sigue excluido de la navegación operativa.

## Archivos modificados en esta tarea

- `app/src/main/java/com/softlutionic/mobile/MainActivity.kt`: enruta Servicios
  a la nueva vista, observa su estado y cierra sus sheets al navegar/destruirse.
- `app/src/main/java/com/softlutionic/mobile/viewmodel/MainViewModel.kt`: búsqueda,
  orden y estado de mutación; reutiliza save/delete con sus callbacks existentes.
- `app/src/main/java/com/softlutionic/mobile/data/repository/SoftlutionicRepository.kt`:
  conserva las mutaciones confirmadas de Servicios ante fallos del GET posterior.

## Archivos nuevos

- `app/src/main/java/com/softlutionic/mobile/ui/services/ServicePresentation.kt`.
- `app/src/main/java/com/softlutionic/mobile/ui/services/ServiceAdapter.kt`.
- `app/src/main/java/com/softlutionic/mobile/ui/services/ServicesController.kt`.
- `app/src/main/java/com/softlutionic/mobile/ui/services/ServiceSheets.kt`.
- `app/src/main/res/layout/view_services.xml`.
- `app/src/main/res/layout/item_service.xml`.
- `app/src/main/res/layout/bottom_sheet_service_form.xml`.
- `app/src/main/res/layout/bottom_sheet_service_detail.xml`.
- `app/src/main/res/drawable/ic_service_build.xml`.
- `app/src/main/res/drawable/ic_service_schedule.xml`.
- `app/src/main/res/values/services.xml`.
- `app/src/test/java/com/softlutionic/mobile/ServicePresentationTest.kt`.
- `app/src/androidTest/java/com/softlutionic/mobile/ServicesSmokeInstrumentation.kt`.
- `scripts/services-preview.init.gradle`.
- Este documento.

Se reutilizan los estilos e input XML oscuros existentes; no se alteraron los
recursos de Clientes. Los demás cambios locales previos se conservaron.

## Cómo probar

1. Con una sesión real, abrir Servicios, buscar por nombre/descripcion y probar
   las cuatro ordenaciones. Revisar la descripción resumida y completa al tocar la card.
2. Abrir Nuevo servicio. Probar requeridos vacíos, precio negativo/fraccionario,
   cero y longitudes máximas. Verificar que no hay campo ID.
3. Registrar datos válidos y verificar el Snackbar y la actualización del listado.
   Editar debe precargar los valores y usar el mismo formulario sin ID editable.
4. Probar permisos sin create/update/delete. Para eliminar, comprobar la confirmación
   y el mensaje de dependencias cuando el servidor devuelva 409.
5. Comprobar sin conexión con una lista previamente guardada; la búsqueda y el orden
   siguen siendo locales. Revisar el formulario con teclado y pantalla pequeña.
6. En Facturación, verificar que la selección de servicios y precios conserva su flujo.

Las altas, ediciones y eliminaciones con una sesión real afectan los datos del
servidor. Las pruebas automatizadas de esta entrega no envían esas mutaciones.

## Validación reproducible

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

La prueba visual aislada usa fixtures exclusivamente en androidTest:

```powershell
.\gradlew.bat --init-script scripts/services-preview.init.gradle test assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.softlutionic.mobile.servicepreview.test/com.softlutionic.mobile.ServicesSmokeInstrumentation
```

Verifica listado, búsqueda, orden numérico, validación sin POST, detalle, precarga,
permisos, vacío y 320 dp con escala de texto 1.4, incluido el mayor entero permitido.
Las capturas con sufijo `test-fixture.png` son datos de prueba. El intento de foco
del input no demuestra por sí solo que el IME del emulador haya sido desplegado.

Ese init script genera un paquete `.servicepreview`, separado de la sesión real.
Al terminar, volver a ejecutar assembleDebug/installDebug sin init script para
generar el paquete habitual `com.softlutionic.mobile`.

## Resultado de esta entrega

- `test`: 25 pruebas aprobadas, 0 fallos. Incluye ocho nuevas de Servicios y las
  pruebas existentes de Clientes, dashboard y mapper.
- `assembleDebug`: BUILD SUCCESSFUL.
- `installDebug`: instalado en Pixel_9 (AVD), Android 17.
- Prueba instrumentada aislada: INSTRUMENTATION_CODE -1, satisfactoria.
- Capturas de listado, ordenamiento, formulario, detalle, edición y pantalla
  estrecha revisadas en `app/build/services-qa/final/`, con datos de prueba.
- Se retiraron los paquetes temporales `.servicepreview` y `.servicepreview.test`.
  El APK normal volvió a generarse e instalarse; aapt confirmó el applicationId
  habitual `com.softlutionic.mobile`.
- `git diff --check`: sin errores. No se hizo commit ni push.
- No se ejecutaron mutaciones reales contra Azure ni una factura real. La
  comprobación de extremo a extremo con datos del servidor requiere una sesión
  autenticada; las consultas anónimas del contrato devolvieron HTTP 401.
