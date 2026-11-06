# Validación previa de Usuarios — 2026-10-27

> Estado actualizado: el usuario autorizó continuar con la evidencia del modelo,
> serializer, API, schema y código web publicado, sin captura de navegador.
> La condición histórica de captura pendiente que figura abajo quedó dispensada.
> La implementación Android se describe en `14_USUARIOS_MOVIL.md`.

## Resultado y límites

El código local de referencia confirma PrimerNombre → «Primer nombre» y
SegundoNombre → «Primer apellido». No se modificaron Android, modelos,
serializers, API ni migraciones. La pantalla especializada queda pendiente de
completar la validación autenticada exigida antes de implementarla.

Backend y web inspeccionados en `C:/proyectos/Softlutionic-Mobile-Work`:

- `apps/seguridad/usuarios/models.py`: modelo `user(AbstractUser)`, tabla Usuarios.
- `apps/seguridad/usuarios/serializers.py`: `UserSerializer`.
- `apps/seguridad/usuarios/api.py`: ViewSet de usuarios.
- `apps/mobile_api/registry.py`: reutiliza UserViewSet; Usuarios requiere Rol administrador.
- `apps/mobile_api/api.py`: SchemaView excluye los campos read_only.
- `frontend/Softlutionic/pages/usuarios.html`: etiquetas del formulario.
- `frontend/Softlutionic/js/usuarios.js`: saveUser, getPayloadFromForm,
  validatePasswords y editUser.

Existen diferencias con la copia de `C:/Users/Logan/Desktop/Proyectos/SoftFactur`;
no se asume que cualquiera de las copias represente exactamente Azure.

Consultas reales sin autenticación al host configurado por defecto en Android,
`https://softlutionic-sistema-eqh8cjdbbre4fndd.westus-01.azurewebsites.net`:

| Ruta | Resultado |
|---|---|
| GET /api/mobile/v1/usuarios/ | HTTP 401: credenciales no provistas |
| GET /api/mobile/v1/schema/ | HTTP 401: credenciales no provistas |

Estos resultados no permiten verificar registros ni schema desplegados.
El payload web se comprobó por su constructor JavaScript; falta una captura
de una solicitud real de la web. No se hicieron escrituras de prueba.

## Mapeo y campos

| Etiqueta visible | Clave API | Tratamiento |
|---|---|---|
| Primer nombre | PrimerNombre | Texto requerido, máximo 50 según modelo local |
| Primer apellido | SegundoNombre | Texto requerido, máximo 50 según modelo local |
| Correo electrónico | Email | El serializer lo vincula a email |
| Usuario | Usuario | El serializer lo vincula a username; web usa correo si está vacío |
| Contraseña | Contrasena | write_only; servidor usa set_password |
| Repetir contraseña | Ninguna | Validación local; nunca se envía |
| Rol | Rol | Booleano: true administrador, false empleado |
| Estado | is_active | Booleano |
| Fecha de registro | FechaRegistro | Solo lectura, asignada por servidor |

Sí: Primer apellido continúa guardándose en SegundoNombre. first_name y
last_name no son inputs del contrato: el modelo los sincroniza en save() desde
PrimerNombre y SegundoNombre cuando estos contienen valor. Participan como
copias internas de compatibilidad Django; no son campos heredados sin uso.

Read-only del serializer: id, rol_descripcion, FechaRegistro, HoraRegistro.
FechaRegistro y HoraRegistro usan auto_now_add. Contrasena es write_only,
no read-only; su hash no se devuelve.

## Payload comprobado en el código web y contrato para Android

Crear: POST /api/mobile/v1/usuarios/ en Android; la web utiliza /api/usuarios/.
Ejemplo ilustrativo, no enviado:

```json
{
  "PrimerNombre": "Andrés",
  "SegundoNombre": "Avilés",
  "Email": "andres@example.com",
  "Usuario": "andres",
  "Contrasena": "<contraseña ingresada>",
  "Rol": false,
  "is_active": true
}
```

Editar: PATCH /api/mobile/v1/usuarios/{id}/ en Android; la web utiliza
/api/usuarios/{id}/. El formulario web envía los mismos seis campos no secretos;
añade Contrasena únicamente cuando se cambia. Omitirla conserva la contraseña.
No enviar confirmación ni fecha. La contraseña se compara sin recortar espacios.

```json
{
  "PrimerNombre": "Andrés",
  "SegundoNombre": "Avilés",
  "Email": "andres@example.com",
  "Usuario": "andres",
  "Rol": false,
  "is_active": true
}
```

El Android actual todavía usa MainActivity.showForm genérico para Usuarios:
recorre m.fields y construye un JSON con los readers; Api.create usa POST y
Api.update usa PATCH. No existe aún un payload especializado de Usuarios
verificado. El formulario genérico no incluye repetición de contraseña, utiliza
las etiquetas del schema y recorta todos los textos, incluida Contrasena.
Por tanto, no debe declararse que ya cumple la referencia funcional solicitada.

## Presentación requerida al implementar

Crear/Editar debe excluir id, last_login, is_superuser, first_name, last_name,
is_staff, date_joined, HoraRegistro y hash de password. Tampoco presentar
rol_descripcion como editable. Usar el id únicamente para la URL de edición.
FechaRegistro se presenta como información del servidor, sin input editable.
La pantalla especializada aún no se implementó: estas exclusiones son requisitos,
no cambios ya aplicados.

Pendiente: acceso administrativo al despliegue para leer ambos GET y comprobar
la petición web real. Después se puede implementar la presentación Android
respetando el contrato confirmado, sin cambios en Django.

## Seguimiento con Pixel_9

El emulador emulator-5554 está conectado y tiene instalada com.softlutionic.mobile.
Se compiló e instaló únicamente el APK de instrumentación, conservando la app
instalada. UsersContractInstrumentation encontró una sesión cifrada y usó el
cliente normal de la aplicación para GET schema. El servidor respondió HTTP 401;
la ejecución se detuvo antes de GET usuarios. Una sesión guardada no garantiza
que sus credenciales sigan siendo aceptadas. El cliente normal intenta renovar
JWT y puede limpiar la sesión si el servidor rechaza la renovación.

No se imprimieron tokens, contraseñas ni valores personales. No se hicieron
POST/PATCH/DELETE de usuarios. Compilación de la instrumentación: exitosa.
Consulta autenticada: no superada por HTTP 401.

También se verificaron por HTTP 200 los archivos publicados en Azure:

- /frontend/Softlutionic/js/usuarios.js
- /frontend/Softlutionic/pages/usuarios.html

El HTML publicado muestra Primer nombre / Primer apellido. Su constructor
getPayloadFromForm usa PrimerNombre / SegundoNombre, Email, Usuario, Rol e
is_active; añade Contrasena al crear o cambiar contraseña y omite fecha y
confirmación. Confirma el mapeo en la web desplegada; todavía no equivale a
capturar una solicitud de guardado de una sesión web real.

Para repetir tras iniciar sesión de nuevo en Android:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am instrument -w com.softlutionic.mobile.test/com.softlutionic.mobile.UsersContractInstrumentation
```

Esta comprobación informa solo claves, metadatos y conteos; no valores personales.

## Revalidación autenticada: exitosa

Después de iniciar sesión nuevamente en Pixel_9 se repitió la instrumentación
contra el mismo host Azure. Ambos GET autenticados finalizaron correctamente
(INSTRUMENTATION_CODE: -1).

- Usuarios devolvió 2 registros. Todos contienen PrimerNombre y SegundoNombre.
- Claves reales: Email, FechaRegistro, HoraRegistro, PrimerNombre, Rol,
  SegundoNombre, Usuario, id, is_active, rol_descripcion.
- No se devuelve password ni Contrasena. Tampoco first_name ni last_name.
- Schema de usuarios: PrimerNombre (CharField requerido, máximo 50),
  SegundoNombre (CharField requerido, máximo 50, etiqueta «Primer apellido»),
  Email (EmailField requerido), Usuario (CharField opcional), Contrasena
  (CharField opcional en schema), Rol e is_active (BooleanField opcionales).
- Permisos reales de la sesión: read/create/update/delete = true.
- El schema excluye id, rol_descripcion, FechaRegistro y HoraRegistro de los
  campos editables, coherente con el serializer inspeccionado.

El schema marca Contrasena como opcional porque así está declarado el campo;
el serializer exige contraseña al crear mediante su validación de objeto.

Esta evidencia sustituye el bloqueo 401 anterior para las consultas móviles.
El mapeo queda confirmado también en la API desplegada. No se crearon, editaron
ni eliminaron usuarios, y no se registraron valores personales.

Única comprobación obligatoria aún pendiente: capturar el payload de una petición
real de guardado en la interfaz web. Su constructor publicado ya fue inspeccionado,
pero eso no se presenta como una captura de tráfico real. La implementación
especializada sigue pendiente hasta completar esa comprobación previa.
