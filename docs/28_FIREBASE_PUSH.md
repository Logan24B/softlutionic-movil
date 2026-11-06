# Firebase Messaging: Android y Django

## Estado

`app/google-services.json` existe y su paquete coincide con `com.softlutionic.mobile`. Se integra Google Services 4.5.0 y Firebase BoM 35.1.0 con `firebase-messaging`. No se añade Analytics. La configuración cliente se excluye de Git; no es una clave privada de servidor.

Android comparte SessionManager, Retrofit y Room entre MainViewModel y FirebaseMessagingService mediante AppGraph. El botón «Activar avisos del teléfono» solicita POST_NOTIFICATIONS desde Android 13; si están bloqueados abre los ajustes correspondientes. La activación registra el token mediante JWT; el inicio autenticado reintenta el registro y onNewToken trata la rotación. El token y el usuario confirmado se guardan cifrados con el mecanismo Keystore existente.

Los mensajes son de datos: `notification_id`, `user_id` y `tipo`. El servicio solo muestra avisos si la sesión existe y el usuario coincide. Omite notificaciones ya leídas en la caché de esa sesión. Las tarjetas del teléfono usan títulos de factura/pago para esos eventos conocidos y un texto genérico; no contienen nombres, importes ni el mensaje financiero. El identificador como tag evita duplicar tarjetas activas. Al pulsar abre el centro de Notificaciones, sin marcar automáticamente todos los avisos. Abrir una tarjeta de la lista usa el PATCH de lectura existente y cancela su aviso local tras confirmación. Con la app abierta se refrescan listado/contador desde Django al llegar el push.

Al salir se invalida primero la asociación local; se intenta revocar el token con un límite de cinco segundos y se limpia la sesión aun cuando falle esa revocación. Los mensajes de la cuenta anterior se descartan localmente. La entrega y la lectura son estados distintos: mostrar o descartar una notificación del teléfono no escribe `leida=true`.

## Backend local

Se añaden `apps/mobile_api/push.py` y `test_push.py`, se conectan las rutas en `urls.py` y la publicación en `apps/transaccion/notificaciones/mongo.py`; se declara firebase-admin en requirements.txt.

- POST `/api/mobile/v1/push/devices/`: cuerpo `token`, opcional `previous_token`; responde `user_id` de la sesión. No se acepta identidad ni rol proporcionado por el cliente.
- POST `/api/mobile/v1/push/devices/unregister/`: cuerpo `token`; elimina únicamente la asociación del usuario autenticado y responde 204.

Ambas rutas requieren JWT y tienen límite de 20 peticiones por minuto por usuario. Los tokens quedan en la colección privada `mobile_push_devices`, dentro de la misma base Mongo configurada en Django. No se modifica SQL ni se añade una migración. La clave del documento es SHA-256 del token; este sigue siendo confidencial y no se devuelve en listados.

Después del commit SQL se persiste la notificación Mongo y se encola su envío FCM. Cada envío revalida la cuenta activa y su rol SQL actual frente a destinatarios_roles; no confía en un rol enviado por Android ni guardado en el dispositivo. Se eliminan tokens que Firebase confirma como no registrados. Los fallos registran solo la clase del error.

## Activación pendiente en el servidor

1. Firebase Console → Configuración del proyecto → Cuentas de servicio → Firebase Admin SDK → Generar nueva clave privada. Guardar únicamente en una ubicación privada del backend, por ejemplo `.private/firebase-service-account.json` (ya ignorada en ese repositorio). No adjuntar ni pegar la clave en el chat.
2. En el entorno del backend configurar `GOOGLE_APPLICATION_CREDENTIALS` con la ruta real y accesible de ese archivo. Como alternativa para un secreto administrado, el código admite `FIREBASE_SERVICE_ACCOUNT_JSON`. No se incorpora ninguna clave a Android ni al repositorio.
3. Instalar requirements.txt y establecer `FCM_ENABLED=true` después de configurar las credenciales. Reiniciar el proceso para recoger cambios de entorno.
4. Desplegar el backend actualizado para que Android encuentre las nuevas rutas. Los cambios locales no modifican el App Service; no se hizo commit, push ni despliegue.
5. En Android iniciar sesión y activar avisos. Comprobar con un evento autorizado de prueba la entrega con la app en primer plano y cerrada, la navegación, el contador y la lectura por usuario. No crear facturas o pagos reales solo para verificar push sin autorización específica.

## Límites

La cola local está acotada a 100 tareas y dos hilos; no es persistente ni tiene reintentos durables. Un reinicio puede perder un envío, pero el aviso persistido sigue disponible mediante la API. Firebase tiene un TTL de cinco minutos en estos mensajes; un dispositivo desconectado puede necesitar actualizar la lista al volver. No se garantiza entrega exactamente una vez ni inmediata. Para entrega recuperable se requiere outbox persistente y worker administrado.

Solo se conectan los eventos existentes de factura_generada y pago_registrado emitidos por este callback Django. No se inventa factura_pagada. Inserciones directas en Atlas, formularios de contacto externos o flujos que no usan ese callback no disparan automáticamente FCM. Para esos productores hay que conectar su publicación o agregar un consumidor de eventos.

La recepción requiere permiso, servicios de Google y acceso a red. Android puede restringir la entrega; tras «Forzar detención» debe reabrirse la app. No se ha probado todavía la recepción remota, registro persistido, cambio de cuenta entre dispositivos ni la revocación real: faltan credenciales y despliegue del backend.

## Validación ejecutada

- Android: 149 pruebas correctas; `test assembleDebug assembleDebugAndroidTest` BUILD SUCCESSFUL. La compilación inicial encontró AccessDeniedException en los JAR del SDK; la ejecución fuera del aislamiento resolvió ese fallo. Persisten avisos de deprecación de métodos de token Firebase y ventanas existentes, sin errores de compilación.
- Backend: 42 pruebas correctas con SQLite en memoria y FCM desactivado; cuatro nuevas comprueban JWT obligatorio, JWT inválido, validación previa al acceso a Mongo y exclusión de identidad/rol enviados por el cliente. No verifican la persistencia Mongo del registro ni el envío real. Las pruebas históricas incluyen dobles de prueba; no se utilizan en ejecución de la aplicación.
- Firebase Admin 7.7.0 instalado para comprobación en `.runtime/push-deps` del backend; importación y construcción de mensaje data-only correctas sin cargar credenciales ni enviar mensajes. No se modificó Python global. Para repetir las comprobaciones locales, añadir esa ruta a PYTHONPATH; el despliegue instala requirements.txt normalmente.
- `git diff --check` correcto en ambos proyectos. APK con certificado compatible verificado; no se ejecutaron instrumentaciones ni se afirma validación visual o push remoto.
