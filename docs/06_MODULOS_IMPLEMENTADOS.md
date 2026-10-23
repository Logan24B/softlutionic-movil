# Módulos implementados

| Módulo | Listar/detalle | Crear/editar | Eliminar |
|---|---|---|---|
| Clientes | Sí | Sí, departamento por selector | Sí, sujeto a PROTECT |
| Departamentos | Sí | Sí | Sí, sujeto a PROTECT |
| Servicios | Sí | Sí | Sí, sujeto a PROTECT |
| Contratos | Sí | Sí, cliente/tipo por selector | Sí, sujeto a PROTECT |
| Tipos de contrato | Sí | Sí | Sí, sujeto a PROTECT |
| Usuarios | Administrador | Administrador; contraseña oculta | Administrador, sujeto a PROTECT |
| Facturas y detalles | Sí | Sí, líneas de servicios atómicas | Bloqueado |
| Pagos | Sí | Sí, factura por selector | Bloqueado |
| Moras | Sí | Calculadas por backend | Bloqueado |
| Notificaciones | Sí | Sí, cliente por selector | Sí |
| Estados de factura | Sí | No | No |
| Auditoría | Administrador | No | No |

Dashboard: tarjetas reales de los dos endpoints web, solo administrador.
Login/refresh/logout, recuperación de sesión, caché y errores de red implementados.
Formularios nativos construidos según schema DRF; validaciones definitivas en serializers originales.
No hay módulos de producto/inventario, datos ficticios ni CRUD en memoria.
