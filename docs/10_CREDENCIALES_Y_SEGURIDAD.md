# Credenciales y seguridad

Android recibe exclusivamente access/refresh JWT. AES/GCM y Android Keystore protegen tokens
persistidos en preferencias privadas. Contraseña solo durante la solicitud de login/formulario
usuario; nunca se guarda en Room ni logs. No logging HTTP de cuerpos o credenciales.
OkHttp valida certificados normalmente; cleartext deshabilitado y BASE_URL exige HTTPS.
Backup Android deshabilitado. Cerrar sesión elimina tokens y caché; no revoca JWT ya emitidos.

Servidor conserva SOFTFACTUR_SECRET_KEY y DB_PASSWORD mediante configuración preexistente.
No hay credenciales de SQL/administrador dentro del proyecto móvil. No se modifica selección
DB_ENV. JWT expira con valores estándar SimpleJWT (access 5 minutos, refresh 1 día); ajustar
SIMPLE_JWT en el servidor si la política organizacional exige otros tiempos.
Roles se verifican contra usuario activo actual en cada solicitud, no solo claims del token.
No se hicieron pruebas mutantes en producción. Pruebas backend usan SQLite efímero aislado.
