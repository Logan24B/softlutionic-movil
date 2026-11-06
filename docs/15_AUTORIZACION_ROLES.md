# Autorización móvil por rol

## Evidencia verificada

Se revisaron SessionManager, MainViewModel, Repository, Api, el serializer/modelo
local y el registro de capabilities del backend en Softlutionic-Mobile-Work.
También se consultaron /auth/me/, /schema/ y /usuarios/ reales en Azure con la
sesión del Pixel_9, sin escrituras ni registro de credenciales/datos personales.

- /auth/me/ devuelve `user`, `permissions`, `dashboard`.
- `user` contiene id, PrimerNombre, SegundoNombre, Email, Usuario, Rol,
  rol_descripcion, FechaRegistro, HoraRegistro, is_active.
- La sesión inspeccionada tiene Rol=true, rol_descripcion=Administrador.
- Usuarios devuelve los mismos campos; no expone is_superuser.
- El schema de Usuarios de esa sesión permite read/create/update/delete.
- El modelo local usa Rol booleano: true Administrador, false Empleado.
  Si is_superuser es true, save() fuerza Rol=true. Por ello un superusuario
  queda indistinguible de un administrador en la respuesta actual.

## Política central

`domain/auth/AuthorizationPolicy.kt` concentra interpretación, intersección de
capabilities, jerarquía, opciones de rol, etiquetas y validación de payload.

- Superusuario: únicamente is_superuser booleano explícito true.
- Administrador: Rol booleano true sin bandera explícita true de superusuario.
- Empleado: Rol booleano false sin bandera explícita true de superusuario.
- Identidad/campos ausentes o inválidos: UNKNOWN/null, acceso sensible denegado.
  UNKNOWN es un estado de datos incompletos, no un rol nuevo de negocio.

No se interpreta nombre, correo, username, descripción de rol ni claims del JWT.
La identidad se obtiene de auth/me, se conserva en Room para esa sesión y se
expone como currentIdentity de solo lectura en ViewModel. Se limpia al logout,
rechazo de sesión y login nuevo; el login nuevo también limpia Room.
Los tokens permanecen en SessionManager con Android Keystore. Ya no se almacena
la respuesta completa de login con tokens en la caché de identidad.

## Restricciones efectivas

Permiso efectivo = schema permission AND regla de rol. Ningún rol añade un
permiso denegado por schema.

Servicios: empleado puede leer, buscar, ordenar, actualizar y abrir detalle;
crear/editar/eliminar se ocultan, los formularios verifican nuevamente y el
Repository bloquea POST/PATCH/DELETE antes de Retrofit. Admin/superusuario
utilizan exclusivamente lo permitido por schema.

Usuarios: empleado no ve el módulo ni sus datos desde caché, y no puede ejecutar
lecturas administrativas o escrituras por los métodos del Repository.
Administrador puede crear empleados y editar empleados, incluyendo contraseña
y estado. Cuentas Rol=true muestran «Cuenta administrativa» y no ofrecen editar.
Si el servidor expone is_superuser=true, se muestra «Superusuario / Cuenta protegida».
Se aplica la jerarquía a las cuentas administrativas sin una excepción de
autoedición añadida; no se agregan otras reglas de autoactivación o autoborrado.
La cuenta actual muestra «Tú». Se conserva la UI previa sin botón de borrado;
la política y el Repository también protegen la operación delete compartida.

El selector de un Admin solo permite Empleado (Rol=false). Un superusuario
explícitamente identificado puede elegir Empleado/Administrador, representados
por el booleano real; no se ofrece crear superusuarios mediante Rol.

Antes de escribir, el Repository vuelve a verificar schema e identidad. Para
editar/eliminar Usuarios obtiene la lista actual del servidor y resuelve el
objetivo por id; no confía en el rol suministrado por el formulario ni en una
copia antigua del objetivo. Rechaza elevación Rol=true de Admin y cualquier
clave ajena a la lista de campos de negocio (incluidos is_superuser/is_staff).
Un cambio de rol concurrente entre lectura y escritura requiere protección del
servidor; Android no puede hacer atómica esa comprobación remota.

403 muestra «No tienes permiso para modificar servicios.» o
«No tienes permiso para administrar esta cuenta.», según el módulo.

## Límites del contrato actual

La API desplegada NO permite identificar separadamente al superusuario.
Por seguridad todos los Rol=true actuales reciben las restricciones de Admin.
No se afirma que ya sea posible administrar administradores desde Android con
la API actual. La rama superusuario está implementada y probada con fixtures
explícitos; falta que el contrato exponga esa identidad para activarla realmente.
No se modificó el backend, sus serializers ni endpoints.

El código de backend inspeccionado concede capabilities amplias a Servicios y
no implementa la jerarquía por objetivo en UserViewSet. Estos cambios Android
son preventivos y no prueban ni sustituyen una protección equivalente del
servidor frente a clientes externos. No se ejecutaron escrituras prohibidas
en producción para comprobarlas.

## Pruebas

`AuthorizationPolicyTest`: 13 casos de mapeo, ausencia de bandera superuser,
identidad anidada, Empleado solo lectura, Admin sobre empleado, bloqueo de
Admin sobre Admin/Superuser/desconocido, elevación de rol y claves Django,
superusuario condicionado a schema, denegaciones backend y caché sin identidad.

Suite `gradlew.bat test`: 43 pruebas, cero fallos.

`gradlew.bat assembleDebug` e `installDebug`: correctos; APK instalado en Pixel_9.
UsersSmokeInstrumentation: correcto, listado/formulario/detalle/edición con
fixtures y sesión administrativa real; ningún guardado válido contra servidor.
ServicesSmokeInstrumentation: correcto, listado/búsqueda/orden, validación,
detalle/edición, permisos, vacío y ancho 320dp con fuente 1.4. Se corrigió la
espera de la prueba para la actualización asíncrona de ListAdapter.
No se hicieron commit ni push.
