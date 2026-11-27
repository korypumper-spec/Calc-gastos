# Declaración de Seguridad de Datos (Google Play Data Safety) — Calculadora de Gastos

Utiliza esta guía de referencia al completar el formulario **Seguridad de los datos** en Google Play Console:

## 1. Datos financieros del usuario
- **¿La app recopila o comparte datos financieros del usuario hacia el exterior?** **No.** Todos los movimientos, presupuestos, metas de ahorro y deudas permanecen únicamente en el almacenamiento local del dispositivo (Room SQLite).

## 2. Publicidad (Google AdMob SDK)
Al integrar el SDK de Google Mobile Ads (`com.google.android.gms:play-services-ads`), Google Play exige declarar los datos que el propio SDK puede recopilar automáticamente cuando los anuncios están activos en producción:
- **Identificadores de dispositivo u otros IDs** (ID de publicidad de Android): recopilados por el SDK de AdMob para publicidad, prevención de fraude y medición.
- **Interacciones con la app y diagnósticos**: impresiones/errores técnicos del banner (sin contenido financiero).
- **Cifrado en tránsito**: Sí (el SDK de Google Mobile Ads usa HTTPS/TLS).
- **Eliminación de datos**: Los datos financieros locales se eliminan desde **Perfil → Borrar todo**.

## 3. Política de Familias / Edad
- Configura el público objetivo en Play Console conforme a usuarios adultos / finanzas personales (no dirigida a niños).
