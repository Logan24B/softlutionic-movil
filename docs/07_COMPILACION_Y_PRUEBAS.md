# Compilación y pruebas

Fecha: 22 de octubre de 2026. Rama mobile-development. Proyecto Android independiente.

## Evidencia ejecutada

- Gradle wrapper 9.5.0 generado correctamente; sincronización/configuración completada.
- `gradlew.bat assembleDebug test --no-daemon`: BUILD SUCCESSFUL.
- 4 pruebas JUnit de RecordMapper: IDs reales, acentos, líneas de factura y exclusión de contraseñas; 0 errores.
- `python manage.py check`: sin incidencias (SOFTFACTUR_DEBUG=true solo en proceso local).
- `python manage.py test apps.mobile_api apps.dashboard apps.transaccion.factura --settings=apps.mobile_api.test_settings --noinput`:
  10 pruebas correctas, SQLite temporal. No conexión ni migración en SQL Server/Azure.
- JWT usuario/correo, refresh, convivencia de sesión web, rechazo anónimo, empleado sin permisos
  administrativos, schema autorizado, CRUD/validación/PROTECT y rollback/total/vencimiento de factura.
- APK instalado en emulador API 37; `am start -W` confirmó Status ok / LaunchState COLD.
- `git diff --check`: sin errores.

Las pruebas usan usuarios y registros efímeros dentro de la base de ensayo, nunca como datos de la app.
Dependencias Python faltantes se instalaron en `.mobile-test-deps/` local ignorado por Git; para
otro entorno instalar requirements.txt normalmente. No se modificó el Python global.
El backend emite aviso de staticfiles no recolectados durante pruebas; no impide los ensayos.

## Entorno y correcciones

SDK 34 y build-tools 36 instalados; JDK 24 y AGP 9.3.1. AndroidX core-ktx 1.13.1 es compatible
con compileSdk 34. Compilación Kotlin en proceso evita permiso bloqueado del daemon externo.
La lectura de jars del SDK por javac requirió ejecución con permisos locales fuera del sandbox.
El AVD de ensayo usa almacenamiento aislado del repositorio en .runtime; no contiene cuentas reales.

APK: app/build/outputs/apk/debug/app-debug.apk.
Informes: app/build/reports/tests/testDebugUnitTest/index.html.
`lintDebug` completado sin errores; conserva recomendaciones sobre recursos de texto, versiones, target SDK, icono y preferencias.
Captura revisada: [login-emulator.png](login-emulator.png). Login vacío validado sin solicitud al servidor.

## Alcance pendiente

Arranque/login nativo verificado sin enviar credenciales a Azure. Falta aceptación de login real,
CRUD completo y reconexión en dispositivo contra servidor HTTPS de ensayo con API desplegada.
No se ejecutaron escrituras en producción, despliegues, migraciones Azure, commit ni push.

Verificación final: `assembleDebug test lintDebug --no-daemon`: BUILD SUCCESSFUL (55 tareas).
4 pruebas Android, 0 fallos. APK final instalado y abierto en frío: Status ok, 3780 ms.
SHA-256 APK: `F23138035038AF311B80893C5E166E2DDC2E88DDF060FAAF264661E846368809`.
