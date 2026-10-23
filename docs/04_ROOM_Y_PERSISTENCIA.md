# Room y persistencia

`CacheDatabase` versión 1, SQLite `softlutionic-cache.db`, tabla cache(key PK, json, updatedAt).
El DAO expone Flow; la UI observa los snapshots mediante el ViewModel. Cada respuesta reemplaza
su clave de recurso (evita duplicación por anexado). Los IDs de las listas son siempre remotos.
Schema Room exportado a app/schemas. JSON conserva nombres/campos exactos del servidor.
La caché incluye datos empresariales privados dentro del almacenamiento interno de Android;
no se cifra la base Room. Tokens AES/GCM con clave Android Keystore en preferencias privadas.
No se guardan contraseñas. Copias Android deshabilitadas. Login exitoso/logout limpian la caché.
No se comparte caché entre cuentas. updatedAt conserva instante de obtención para extensiones.
