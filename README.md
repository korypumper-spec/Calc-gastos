# Calculadora de Gastos

Aplicación Android nativa de finanzas personales pensada inicialmente para México (MXN), construida con **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **WorkManager**, **BiometricPrompt**, **Android Keystore** y **Google AdMob**.

> **Nota de entorno:** Este repositorio incluye tanto el proyecto Android nativo completo (raíz Gradle + `app/` + `.github/workflows/android.yml`) como una vista previa web interactiva (`src/` + `index.html`) para pruebas rápidas en navegador.

---

## 1. Descargar el proyecto
```bash
git clone <URL_DE_TU_REPOSITORIO>
cd <NOMBRE_DE_LA_CARPETA>
```

## 2. Abrir en Android Studio
1. Instala **Android Studio Ladybug (2024.2)** o superior con **JDK 17**.
2. Abre la carpeta raíz del proyecto en Android Studio (`File → Open`).
3. Espera a que Android Studio sincronice Gradle y descargue las dependencias declaradas en `gradle/libs.versions.toml`.

## 3. Compilar localmente (Windows / macOS / Linux)
Asegúrate de tener configurado **Java 17** y **Android SDK (API 35)** (`ANDROID_HOME`).
Si usas Gradle instalado o generas el wrapper JAR desde Android Studio (`gradle wrapper`), ejecuta:
```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
```
*(En entornos sin `gradle-wrapper.jar` en el repo, ejecuta primero `gradle wrapper` o usa `gradle <tarea>` directamente con Gradle 8.9).*

## 4. Compilar automáticamente desde GitHub Actions
1. Sube el proyecto a un repositorio de GitHub (`push` a `main`, `master` o `develop`).
2. Ve a la pestaña **Actions** en GitHub.
3. El workflow `.github/workflows/android.yml` ejecutará automáticamente:
   - Pruebas unitarias (`testDebugUnitTest`)
   - Lint (`lintDebug`)
   - APK de depuración (`assembleDebug`)
   - APK de lanzamiento (`assembleRelease`)
   - Android App Bundle (`bundleRelease`)

## 5. Descargar e instalar el APK en un dispositivo Android
1. En GitHub → **Actions**, abre la ejecución exitosa más reciente.
2. En la sección **Artifacts**, descarga `mi-bolsillo-inteligente-artefactos`.
3. Descomprime el archivo y copia `app-debug.apk` a tu teléfono Android.
4. En Android, abre el archivo APK y habilita **"Instalar apps desconocidas"** si el sistema lo solicita.

## 6. Generar el Android App Bundle (.aab) y configurar firma de producción
Nunca subas archivos `.jks` / `.keystore` ni contraseñas al repositorio.

1. Genera tu llave de firma localmente:
   ```bash
   keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mi_bolsillo_key
   ```
2. En GitHub → **Settings → Secrets and variables → Actions**, agrega los secretos:
   - `ANDROID_KEYSTORE_PATH` (ruta donde tu runner o paso previo decodifique el keystore)
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
3. Si las variables de entorno no están definidas, `assembleRelease` y `bundleRelease` compilan correctamente **sin firma** para validación en CI.

## 7. Configurar Google AdMob para producción
- En `debug`, la app usa los **IDs oficiales de prueba de Google AdMob** (`ca-app-pub-3940256099942544~3347511713` y `ca-app-pub-3940256099942544/9214589741`).
- En `release`, configura estos secretos o variables de entorno con tus IDs reales de AdMob Console:
  - `ADMOB_APP_ID_RELEASE`
  - `ADMOB_BANNER_ID_RELEASE`
- Si `ADMOB_BANNER_ID_RELEASE` está vacío, la app desactiva las solicitudes de anuncios en release automáticamente sin mostrar errores.

## 8. Configuraciones que requieren credenciales externas
1. **Firma de producción (`.jks` + contraseñas)** para subir el `.aab` a Google Play.
2. **AdMob App ID y Banner Unit ID reales** para monetización en producción.
3. **URL pública de Política de Privacidad** (personalizando `PRIVACY_POLICY.md`).
4. **Google Play Billing SKU** (la compra para quitar anuncios está deshabilitada en código hasta registrar el producto en Play Console).

## 9. Publicar en Google Play Console
1. Personaliza `PRIVACY_POLICY.md` y publícalo en una URL HTTPS.
2. Completa la sección **Seguridad de los datos** usando `PLAY_STORE_DATA_SAFETY.md`.
3. Compila el `.aab` firmado (`bundleRelease`) y súbelo a prueba interna / producción en Google Play Console.
