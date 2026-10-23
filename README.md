# Softlutionic Android

Aplicación Android nativa del sistema empresarial Softlutionic; backend Django existente vía HTTPS.
Proyecto independiente y extraíble: copiar solamente esta carpeta y abrirla en Android Studio.

## Requisitos y ejecución

Android Studio compatible con AGP 9.3.1, Gradle wrapper 9.5.0, JDK compatible (verificado con 24),
Android SDK 34, dispositivo/emulador API 29 o superior. Kotlin integrado, XML/ViewBinding,
Material, RecyclerView, Coroutines/StateFlow, MVVM/Repository, Retrofit/Gson/OkHttp y Room/SQLite.

Abrir esta carpeta → sincronizar Gradle → seleccionar dispositivo → Run app.
En PowerShell con ANDROID_HOME configurado:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

La URL de producción se define una vez en app/build.gradle.kts y genera BuildConfig.BASE_URL.
Para ensayo: `-PSOFTLUTIONIC_BASE_URL=https://servidor-de-pruebas/` (barra final obligatoria).
El servidor debe tener desplegado `/api/mobile/v1/`; el cliente no crea esos endpoints en Azure.
Login con usuario/correo y contraseña existentes. No hay cuenta predeterminada ni credenciales SQL.

## Funcionalidad y arquitectura

MainActivity → MainViewModel → SoftlutionicRepository → Retrofit / CacheDao → Room.
DTO de schema define campos reales, permisos y relaciones; RecyclerView representa los registros;
formularios Material con selectores remotos, detalle, confirmación de eliminación y validación.
Clientes, departamentos, servicios, contratos, tipos, usuarios, facturas con líneas, pagos,
moras, notificaciones, estados y auditoría según permisos. Dashboard conserva KPIs web.
JWT se renueva de forma controlada; tokens cifrados con Keystore. Logout limpia caché y tokens.
Room conserva snapshots observables para lectura sin red y se actualiza al recuperar conexión.
Escrituras requieren red. No se usa WebView ni conexión directa a SQL.

## Estructura

- app/src/main/java/com/softlutionic/mobile/data: API/DTO, Room/DAO, mapper, repositorio y sesión.
- domain/model: modelo de presentación Record.
- viewmodel: MainViewModel y estados.
- ui y res/layout: RecyclerView, UI nativa y XML/ViewBinding.
- gradle/wrapper: herramientas portátiles de compilación.
- docs/01…10: auditoría, API, arquitectura, persistencia, pruebas, pendientes y seguridad.

Consultar [guía Android Studio](docs/09_GUIA_ANDROID_STUDIO.md),
[API](docs/03_API_MOVIL.md), [módulos](docs/06_MODULOS_IMPLEMENTADOS.md) y
[pendientes reales](docs/08_PENDIENTES.md). Los cambios Django necesarios se documentan en
BACKEND_CHANGES.md del directorio mobile del repositorio padre; Android no depende de esa ruta.
