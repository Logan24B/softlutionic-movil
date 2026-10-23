# Arquitectura Android

Proyecto independiente `com.softlutionic.mobile`, minSdk 29, compile/target 34 instalado.
Gradle Kotlin DSL, AGP 9.3.1, Gradle 9.5.0, Kotlin integrado en AGP, KSP, Java bytecode 17.
Activity presenta XML/ViewBinding y componentes Material; MainViewModel maneja estados/coroutines;
SoftlutionicRepository coordina Retrofit y DAO; Room conserva snapshots JSON por recurso.
RecordMapper separa JSON de modelos de presentación. DTO de autenticación, módulos y permisos
son tipados; recursos empresariales conservan JsonObject porque el formulario se genera desde
schema real. Las entidades Room nunca son DTO de Retrofit.

La navegación usa selector de módulos, listas RecyclerView y diálogos nativos de detalle/formulario.
Facturas agregan editor de servicios, cantidad y precio con envío atómico. No hay SQL en UI.
Los campos de formulario se adaptan a tipos y relaciones entregados por el backend.

Referencia de Kotlin integrado: https://developer.android.com/build/migrate-to-built-in-kotlin
