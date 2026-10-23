# Sincronización

Lecturas: Room → Flow → UI inmediatamente; actualización API reemplaza snapshot Room.
Sin red: se conserva última respuesta, con aviso. Schema autorizado también queda en caché.
NetworkCallback vuelve a solicitar el módulo seleccionado al recuperar red; botón Actualizar
permite reintentar. No hay servicio de fondo ni cola de escrituras. Crear/editar/eliminar requieren
conexión y nunca se presentan como sincronizados antes de la respuesta del servidor.

No se reintentan automáticamente POST. Facturas usan código único y pagos recibo único en servidor.
Un timeout posterior al guardado exige actualizar antes de repetir para comprobar el resultado.
Las renovaciones JWT se serializan; reutilizan access renovado en solicitudes concurrentes.
Refresh inválido limpia sesión; fallo de transporte mantiene tokens para reintentar al volver la red.
Los permisos offline son la última copia y el servidor siempre valida las escrituras.
