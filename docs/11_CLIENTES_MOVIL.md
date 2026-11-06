# Clientes: listado, detalle y formulario

## Alcance

Presentación específica de Clientes sobre el mismo `MainViewModel`, Repository,
Retrofit, JWT y Room. No cambia el backend, las rutas, el CRUD de otros módulos,
el login ni el dashboard. No incorpora dependencias, fotografías ni datos de
demostración en la aplicación distribuida. No se hizo commit ni push.

## Contrato inspeccionado

Se leyeron los archivos locales del backend de referencia en
`C:/proyectos/Softlutionic-Mobile-Work`, sin modificarlos:

- `apps/catalogos/clientes/serializers.py` y `models.py`.
- `apps/catalogos/departamentos/serializers.py` y `models.py`.
- `apps/catalogos/clientes/api.py`.
- `apps/mobile_api/api.py` (`SchemaView`) y `registry.py`.

El serializer de Cliente expone `departamento_detalle: {id, Nombre}` y declara
de solo lectura `id`, `departamento_detalle`, `FechaRegistro` y `HoraRegistro`.
El schema excluye los campos de solo lectura y describe los editables con
`name`, `required`, `type`, `relation` y `max_length`.

La consulta directa a `/api/mobile/v1/clientes/` devolvió HTTP 401 sin sesión;
la consulta de schema encontró errores de conexión DNS/TLS en este entorno.
No se obtuvo una muestra autenticada del despliegue actual. El contrato local
no se presenta como una verificación del contenido desplegado en Azure.
La app usa el schema de la sesión real al construir el formulario y sus permisos.

## Comportamiento

- **Listado:** tarjetas con nombre completo, iniciales, departamento, teléfono,
  correo, cédula y badge de estado. Un estado ausente se presenta como desconocido,
  no como inactivo. Los IDs no aparecen en las tarjetas.
- **Búsqueda:** sobre Nombre, Apellido, Cedula, Telefono y Correo de los registros
  cargados. Ignora mayúsculas y acentos; acepta números de teléfono/cédula sin
  separadores. No hace solicitudes por carácter. Su alcance es la lista cargada,
  no datos que el servidor no haya retornado.
- **Filtros:** sheet compacto con estado y departamento, combinables con búsqueda.
  Aplicar actualiza la lista local; Limpiar restablece los filtros conservando la
  consulta escrita. El estado vive en el ViewModel y se restablece al cerrar sesión.
- **Crear:** botón destacado de ancho ajustado al contenido. Abre un Bottom Sheet
  oscuro expandido con secciones de datos personales, contacto, ubicación y estado.
  Usa TextInputLayout/TextInputEditText, dropdown Material y MaterialSwitch.
- **Editar:** el mismo formulario precarga los valores del cliente y utiliza PATCH.
  No agrega un segundo CRUD. El payload se limita a la intersección del schema con
  `Nombre`, `Apellido`, `Cedula`, `Telefono`, `Correo`, `Direccion`, `DepartamentoId`
  y `Estado`, excluyendo cualquier campo marcado `read_only`.
- **Validación:** requeridos y longitudes según schema, correo razonable, teléfono
  nicaragüense de 8 dígitos o prefijo 505 y departamento seleccionado. Antes de
  enviar, el teléfono se normaliza a `+505 0000 0000`, compatible con el validator
  y serializer inspeccionados. Los errores de campo de HTTP 400/409/422 se muestran
  debajo del campo con etiquetas legibles; también se admiten errores generales JSON.
- **Guardar:** progreso, controles bloqueados y guardia en ViewModel contra dos
  mutaciones simultáneas de Clientes. Ante un error conserva el formulario. Tras
  confirmación del servidor cierra y muestra Snackbar. El repositorio actualiza
  Room y refresca; si falla ese GET posterior, conserva la mutación ya confirmada
  para evitar sugerir un POST duplicado.
- **Detalle:** cabecera con avatar, estado y departamento; datos personales y de
  contacto; ID, fecha y hora en una sección de información del sistema. Estos
  datos se muestran como texto, nunca como entradas editables. Fechas y horas se
  formatean en español cuando el valor recibido es reconocible.
- **Permisos:** crear, editar y eliminar dependen del schema. La eliminación pide
  confirmación y HTTP 409 muestra que el cliente tiene registros relacionados.
  Los permisos vuelven a comprobarse antes de ejecutar las acciones.
- **Vacío/carga/offline:** mensajes de carga, vacío y búsqueda sin coincidencias.
  Room conserva el listado visible cuando falla una actualización. Se mantiene
  el banner de conectividad existente. El formulario y detalle usan scroll anidado,
  y el diálogo solicita ajuste de tamaño con el teclado.

## DepartamentoId a nombre

El ViewModel toma la ruta `relation` del campo DepartamentoId del schema y consulta
el catálogo mediante el repositorio compartido (`options`), almacenándolo en Room.
La presentación prioriza ese catálogo y puede usar `departamento_detalle.Nombre`
incluido en los registros como respaldo. No hay departamentos hardcodeados.

El dropdown muestra nombres y conserva el ID internamente. Al editar se conserva
la relación actual aun si no se puede renovar el catálogo. Cuando no existe nombre
disponible se indica «Departamento no disponible»; nunca se sustituye por el ID.
Cerrar sesión o invalidar la sesión limpia el catálogo en memoria y los filtros.

## Archivos modificados en esta tarea

- `app/src/main/java/com/softlutionic/mobile/MainActivity.kt`: enruta Clientes a su
  presentación, conecta los StateFlow y cierra sheets al salir o destruir la actividad.
- `app/src/main/java/com/softlutionic/mobile/viewmodel/MainViewModel.kt`: filtros,
  catálogo, estado de guardado, callbacks opcionales del CRUD y limpieza por sesión.
- `app/src/main/java/com/softlutionic/mobile/data/repository/SoftlutionicRepository.kt`:
  caché de catálogos y conservación de operaciones de Clientes ya confirmadas.
- `app/src/main/java/com/softlutionic/mobile/data/remote/Api.kt`: propiedad opcional
  `Field.read_only`, compatible con el schema actual que omite dichos campos.

## Archivos creados

- `app/src/main/java/com/softlutionic/mobile/ui/clients/ClientPresentation.kt`.
- `app/src/main/java/com/softlutionic/mobile/ui/clients/ClientAdapter.kt`.
- `app/src/main/java/com/softlutionic/mobile/ui/clients/ClientsController.kt`.
- `app/src/main/java/com/softlutionic/mobile/ui/clients/ClientSheets.kt`.
- `app/src/main/res/layout/view_clients.xml`, `item_client.xml`,
  `bottom_sheet_client_form.xml`, `bottom_sheet_client_detail.xml`,
  `client_input.xml`, `client_dropdown.xml`, `client_dropdown_item.xml`.
- `app/src/main/res/drawable/client_avatar.xml`, `ic_client_phone.xml`,
  `ic_client_mail.xml`.
- `app/src/main/res/values/clients.xml`: textos y estilos específicos.
- `app/src/test/java/com/softlutionic/mobile/ClientPresentationTest.kt`.
- `app/src/androidTest/java/com/softlutionic/mobile/ClientsSmokeInstrumentation.kt`.
- `scripts/clients-preview.init.gradle`: variante aislada y runner visual opcionales.
- Este documento.

Los demás cambios que ya estaban en el árbol de trabajo se conservaron.

## Cómo probar con una sesión real

1. Entrar a Clientes. Comprobar nombres de departamentos y datos del catálogo real.
2. Buscar por nombre sin acento, teléfono sin espacios, cédula o correo. Combinar
   estado y departamento; limpiar filtros y borrar la búsqueda.
3. Crear un cliente con campos vacíos o correo incorrecto: deben aparecer errores
   locales sin POST. Elegir un departamento y completar datos válidos para registrar.
4. Tocar la tarjeta, revisar fecha/hora e ID en detalle; editar y comprobar la
   precarga. FechaRegistro, HoraRegistro e ID no deben aparecer como inputs.
5. Con los permisos apropiados, verificar confirmación de eliminar y el mensaje de
   dependencias cuando el servidor devuelva 409. Estas acciones escriben datos reales.
6. Sin conexión, revisar los clientes y el catálogo previamente guardados; cambiar
   búsqueda y filtros debe seguir funcionando. Un guardado fallido debe conservar
   los datos escritos en el formulario.
7. Repetir con pantalla estrecha, texto ampliado y teclado abierto.

## Validación automatizada

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

La prueba instrumentada usa exclusivamente fixtures en `androidTest`. Nunca pulsa
Guardar con un formulario válido ni confirma un DELETE. Verifica renderizado,
búsqueda, combinación de filtros, validación, precarga, detalle, permisos, estado
vacío y ancho de 320 dp con escala de texto 1.4.

Para ejecutarla sin tocar la sesión de la app real:

```powershell
.\gradlew.bat --init-script scripts/clients-preview.init.gradle assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.softlutionic.mobile.clientpreview.test/com.softlutionic.mobile.ClientsSmokeInstrumentation
```

Los APK generados con ese init script usan `.clientpreview`. Al terminar, ejecutar
de nuevo `assembleDebug` e `installDebug` sin init script para generar/instalar el
paquete normal. Las capturas se identifican con el sufijo `test-fixture.png`.

### Resultado de esta entrega

- `test`: 15 pruebas aprobadas, 0 fallos (8 de Clientes, 3 de dashboard y 4 de mapper).
- `assembleDebug`: BUILD SUCCESSFUL.
- `installDebug`: instalado en Pixel_9 (AVD), Android 17.
- Prueba instrumentada aislada: INSTRUMENTATION_CODE -1; listado, búsqueda,
  filtros, validación, precarga, detalle, permisos, vacío y texto ampliado aprobados.
- Capturas del listado, filtros, formulario, detalle, edición y ancho reducido
  revisadas bajo `app/build/clients-qa/final/`. Son datos de prueba identificados.
- Los paquetes `.clientpreview` y `.clientpreview.test` se retiraron después de
  la revisión. El APK final volvió a generarse e instalarse con el ID normal
  `com.softlutionic.mobile`; se comprobó su identidad con `aapt`.
- `git diff --check`: sin errores. No se hizo commit ni push.
- No se verificaron altas/ediciones/eliminaciones contra Azure: no se proporcionó
  una sesión autenticada y la prueba visual no envía mutaciones reales.
