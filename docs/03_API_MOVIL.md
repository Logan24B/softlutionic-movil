# API móvil v1

Base HTTPS central en BuildConfig.BASE_URL. Prefijo `/api/mobile/v1/`.

| Ruta | Método | Contrato |
|---|---|---|
| auth/login/ | POST | username (usuario o correo), password → access, refresh, user, permissions, dashboard |
| auth/refresh/ | POST | refresh → access |
| auth/me/ | GET | user, permissions, dashboard |
| schema/ | GET | módulos autorizados, campos, tipos, relaciones, capabilities |
| dashboard/facturacion/ | GET | cards, charts, tables originales; administrador |
| dashboard/clientes-servicios/ | GET | cards, charts, tables originales; administrador |
| {modulo}/ | GET, POST | lista JSON sin paginación; POST según capabilities |
| {modulo}/{id}/ | GET, PATCH, PUT, DELETE | operación según capabilities |

Módulos: clientes, departamentos, servicios, usuarios, contratos, tipos-contrato, facturas,
estados-factura, pagos, moras, notificaciones, auditoria. No se crea otro modelo empresarial.
Auditoría/usuarios exclusivos de administrador; moras/estados/auditoría consulta; DELETE de
factura/pago bloqueado. Usuarios mantiene además permiso original. Empleados operan los demás
catálogos y transacciones según política existente. 401 autenticación, 403 permisos, 400 validación,
404 inexistente, 409 duplicidad/dependencias. Errores desconocidos conservan gestión DRF.

Factura recibe campos existentes más `lineas: [{ServicioId, Cantidad, PrecioVenta?}]`.
UsuarioId se asigna desde JWT; totales/estado/vencimiento se calculan en Django. PATCH con
lineas reemplaza todos los detalles dentro de la transacción; si falla una línea se revierte todo.
No borrar facturas/pagos por API móvil. Probar creación/edición solamente en base de ensayo.
Login limita solicitudes anónimas a 10/minuto por IP; considerar proxy al desplegar.

JWT coexiste con sesiones web; no se cambia REST_FRAMEWORK global ni se requiere migración nueva.
No hay blacklist/revocación remota en esta versión; logout borra tokens locales.
Referencia: https://django-rest-framework-simplejwt.readthedocs.io/en/stable/getting_started.html
