# Usuarios móvil

## Contrato conservado

Primer nombre usa `PrimerNombre`; Primer apellido usa `SegundoNombre`.
SegundoNombre conserva exactamente su nombre interno. No se cambió backend,
modelo, serializer ni migraciones. La evidencia previa y el schema autenticado
de Azure se documentan en `13_VALIDACION_USUARIOS.md`. Por instrucción expresa
del usuario no se necesita una captura adicional del navegador.

El modelo local sincroniza first_name y last_name al guardar, respectivamente
desde PrimerNombre y SegundoNombre. Android no los presenta ni los envía.

## Interfaz

Listado con tarjetas, nombre completo, correo, usuario, rol y estado; búsqueda
sin distinción de acentos, filtros por rol/estado y orden alfabético. Consulta,
detalle, crear y editar reutilizan API, JWT, repositorio y caché existentes.
Los permisos recibidos controlan la creación y edición. Se conserva el estado
de búsqueda/filtros en ViewModel y se limpia al cerrar sesión.

Crear/Editar muestra exclusivamente los campos de negocio editables presentes
en schema. Formulario desplazable, entradas Material, visibilidad de contraseña,
confirmación local, errores de campos, progreso y bloqueo de doble guardado.
Estado se modifica al editar, como booleano is_active. No se ofrece borrado
destructivo desde esta presentación; la referencia web usa activar/inactivar.

FechaRegistro aparece como información de solo lectura; al crear indica
«Asignada por el servidor». Android nunca genera ni envía su valor.

No se carga contraseña existente ni hash, incluso si una respuesta incorrecta
los suministrara. Al editar, ambos inputs secretos comienzan vacíos. La contraseña
se conserva exactamente, incluidos espacios, y se omite si no cambia.

Campos excluidos del formulario y payload: id, last_login, is_superuser,
first_name, last_name, is_staff, date_joined, HoraRegistro, password/hash y
rol_descripcion. Solo se usa id internamente en la URL de edición.

## Payload

POST `/api/mobile/v1/usuarios/`:

```json
{
  "PrimerNombre": "Andrés",
  "SegundoNombre": "Avilés",
  "Email": "andres@example.com",
  "Usuario": "andres",
  "Contrasena": "<valor ingresado>",
  "Rol": false,
  "is_active": true
}
```

PATCH `/api/mobile/v1/usuarios/{id}/`: los mismos campos, omitiendo Contrasena
cuando no cambia. Usuario vacío utiliza el correo, igual que la web.
Repetir contraseña nunca es parte del JSON. La lista permitida de claves se
intersecta con los campos editables reales del schema.

Read-only del servidor: id, rol_descripcion, FechaRegistro y HoraRegistro.
Contrasena es write-only; el servidor exige una al crear aunque schema marque
required=false para permitir edición sin cambio.

## Verificación

- `gradlew.bat test`: 30 pruebas, cero fallos, incluidas cinco de contrato de Usuarios.
- `gradlew.bat assembleDebug`: correcto.
- `gradlew.bat installDebug`: correcto, instalado en Pixel_9 (un dispositivo).
- UsersSmokeInstrumentation en Pixel_9: correcto (INSTRUMENTATION_CODE -1).
  Comprueba listado, etiquetas, validación de envío inválido, detalle,
  precarga al editar y exclusión de secretos/campos internos con fixtures.
- Capturas revisadas en `docs/verification/users/create.png` y `list.png`.
- El guardado válido contra producción no se ejecutó; el contrato del payload
  está cubierto por pruebas unitarias y se reutiliza el CRUD existente.
- No se hicieron escrituras de prueba contra usuarios del servidor.
- No se hizo commit ni push.
