# Filtros de Notificaciones y alcance de avisos del dispositivo

Se agregan opciones permanentes Todos, Leída y No leída, incluso con lista vacía. Filtrar por fecha abre selectores Desde/Hasta, valida que el rango no esté invertido y permite limpiar solo las fechas. Los filtros se combinan con título/mensaje y lectura, conservando la selección en MainViewModel. La fecha de creación ISO-8601 se compara según el día local del dispositivo; los límites son inclusivos. No se incluyen fechas desconocidas al aplicar un rango. El contador remoto de no leídas conserva su significado global y no depende de los filtros locales.

Archivos: ConsultationPresentation.kt, ConsultationController.kt y ConsultationPresentationTest.kt. El test adicional verifica combinación de búsqueda/lectura/fecha, día local, límites inclusivos y fecha ausente.

Los avisos del dispositivo se están definiendo con el usuario: solo con la app abierta o también cerrada. No existe infraestructura FCM/push en Android ni en el backend inspeccionado. No se han añadido dependencias Firebase, permisos, polling ni datos de demostración. El backend genera factura_generada y pago_registrado; esto no acredita un evento factura_pagada. Se requiere acordar el canal de entrega y utilizar solo eventos persistidos del servidor, sin deducir eventos financieros desde la UI.

Validación: 149 pruebas unitarias correctas, cero fallos; `assembleDebug assembleDebugAndroidTest` correcto; `git diff --check` correcto. APK firmado con la clave debug compatible existente. No se ejecutó validación visual ni instrumentaciones de interfaz con datos simulados.
