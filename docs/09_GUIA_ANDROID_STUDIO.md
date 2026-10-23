# Abrir en Android Studio

1. Copiar completa esta carpeta SoftlutionicAndroid (incluidos gradle/, gradlew y gradlew.bat).
2. Open → seleccionar esta carpeta, no el repositorio Django completo.
3. Usar Android Studio compatible con AGP 9.3.1; instalar Android SDK 34 y JDK compatible con Gradle 9.5.
4. Configurar SDK en Android Studio/local.properties local, nunca versionar esa ruta.
5. Sync Project with Gradle Files. La primera sincronización descarga dependencias.
6. Seleccionar dispositivo API 29+ y Run app.
7. Ingresar usuario o correo y contraseña del backend cuyo API móvil ya esté desplegado.

BASE_URL predeterminada de producción en app/build.gradle.kts. Cambiar con propiedad Gradle
`SOFTLUTIONIC_BASE_URL=https://servidor-de-pruebas/` o `-PSOFTLUTIONIC_BASE_URL=...`.
Siempre HTTPS con certificado válido. Para backend local usar terminación TLS de desarrollo
confiable por el dispositivo; esta aplicación no permite HTTP ni certificados ignorados.
No copiar secretos Django/SQL dentro de Gradle ni Android.
