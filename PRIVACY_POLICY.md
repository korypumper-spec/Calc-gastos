# Política de Privacidad — Calculadora de Gastos

> **[PENDIENTE DE PERSONALIZACIÓN]**: Antes de publicar en Google Play Store, reemplaza los campos marcados entre corchetes (`[...]`) con los datos reales de la persona o entidad desarrolladora y aloja este documento en una URL pública HTTPS.

**Última actualización:** [PENDIENTE DE PERSONALIZACIÓN: Fecha]  
**Responsable / Desarrollador:** [PENDIENTE DE PERSONALIZACIÓN: Nombre o razón social]  
**Correo de contacto:** [PENDIENTE DE PERSONALIZACIÓN: correo@ejemplo.com]

## 1. Principio de privacidad financiera local
**Calculadora de Gastos** está diseñada para funcionar sin conexión a internet en todas sus funciones financieras esenciales.
- No requiere registro de cuenta, correo electrónico ni contraseña.
- No solicita vinculación de cuentas bancarias ni credenciales financieras.
- Todos tus ingresos, gastos, presupuestos, pagos programados, metas de ahorro y deudas se almacenan **exclusivamente en tu dispositivo** mediante una base de datos local (Room / SQLite).

## 2. Datos que NO recopilamos ni transmitimos
El desarrollador no tiene acceso, no recopila y no envía a servidores propios ni de terceros:
- Importes, saldos, categorías o descripciones de tus movimientos.
- Tus presupuestos, metas de ahorro o historial de deudas.
- Archivos CSV exportados (solo se generan cuando tú presionas explícitamente *Exportar CSV* y eliges con qué aplicación compartirlos).

## 3. Seguridad y control de tus datos
- La aplicación desactiva la copia de seguridad automática no cifrada en la nube (`android:allowBackup="false"`).
- Puedes activar un bloqueo opcional mediante biometría o credenciales del dispositivo (PIN/patrón).
- Puedes eliminar permanentemente todos los datos locales en cualquier momento desde **Perfil y configuración → Borrar todo**.

## 4. Publicidad discreta (Google AdMob)
La versión gratuita muestra anuncios de banner adaptativos en secciones informativas mediante el SDK oficial de Google Mobile Ads y la plataforma de consentimiento UMP (User Messaging Platform).
- Google AdMob puede recopilar identificadores técnicos del dispositivo e información de interacción publicitaria conforme a la Política de Privacidad de Google.
- **Nunca** se comparten tus datos financieros ni descripciones de movimientos con redes publicitarias.

## 5. Permisos utilizados
- `INTERNET` y `ACCESS_NETWORK_STATE`: utilizados únicamente para la carga de banners de AdMob cuando hay conexión.
- `POST_NOTIFICATIONS` (opcional en Android 13+): utilizado solo si activas los recordatorios locales de pagos. La aplicación sigue funcionando normalmente si rechazas este permiso.
