# Guía de Configuración - ZoomAccess Backend

Esta guía detalla los pasos necesarios para configurar y conectar el backend de **ZoomAccess** con las APIs de **Google Sheets** y **Zoom Webhooks**.

---

## 📋 Requisitos Previos

El backend necesita tres valores clave configurados en el archivo [application.properties](file:///c:/Users/franz%20schwartz/Documents/PROYECTOS-PERSONALES/zoomaccess/src/main/resources/application.properties):
1. **Credenciales de Google (JSON de Cuenta de Servicio)**.
2. **ID de la Hoja de Cálculo de Google Sheets**.
3. **Token Secreto de Zoom Webhook**.

---

## 1. Configuración de Google Sheets (Base de Datos)

El sistema utiliza una cuenta de servicio de Google Cloud para escribir de manera no bloqueante en una hoja de cálculo sin requerir la interacción o consentimiento manual del usuario.

### Paso 1.1: Crear Proyecto y Cuenta de Servicio en Google Cloud
1. Entra a la [Google Cloud Console](https://console.cloud.google.com/).
2. Crea un nuevo proyecto (ej. `zoom-access-logs`).
3. En la barra de búsqueda superior, busca **Google Sheets API** y haz clic en **Habilitar (Enable)**.
4. Dirígete a **IAM & Administration** (IAM y Administración) > **Service Accounts** (Cuentas de Servicio).
5. Haz clic en **Create Service Account** (Crear Cuenta de Servicio):
   - Dale un nombre (ej: `sheets-logger`).
   - Haz clic en **Create and Continue** (Crear y Continuar).
   - Opcionalmente, puedes dejar los roles vacíos (no requiere roles de proyecto ya que el acceso se da directamente en la hoja).
   - Haz clic en **Done** (Listo).

### Paso 1.2: Generar y Descargar la Clave Privada JSON
1. En la lista de Cuentas de Servicio, haz clic sobre el correo electrónico de la cuenta que acabas de crear.
2. Ve a la pestaña **Keys** (Claves).
3. Haz clic en **Add Key** (Agregar Clave) > **Create New Key** (Crear nueva clave).
4. Selecciona el formato **JSON** y haz clic en **Create** (Crear).
5. Se descargará automáticamente un archivo `.json` a tu computadora.
6. Cambia el nombre de este archivo a `credentials.json`.
7. Muévelo al directorio de recursos del proyecto en: `src/main/resources/credentials.json`.
   > [!IMPORTANT]
   > Nunca subas el archivo `credentials.json` a repositorios públicos de GitHub. Ya está incluido en el `.gitignore` del proyecto para evitar fugas de credenciales.

### Paso 1.3: Crear la Hoja de Cálculo y Compartir Acceso
1. Abre tu Google Drive y crea una nueva **Hoja de cálculo de Google (Google Sheets)**.
2. Copia el **Spreadsheet ID** de la URL del navegador.
   - Ejemplo de URL: `https://docs.google.com/spreadsheets/d/1X2y3z4w5v6u7t8s9r0pQWERTY/edit`
   - El ID es: `1X2y3z4w5v6u7t8s9r0pQWERTY`
3. Abre el archivo de credenciales `credentials.json` descargado y copia el valor del campo `"client_email"` (ej: `sheets-logger@your-project-id.iam.gserviceaccount.com`).
4. En tu Google Sheet, haz clic en el botón superior derecho **Compartir (Share)**.
5. Pega el correo de la cuenta de servicio y otórgale permisos de **Editor**. Desmarca "Enviar notificación" y haz clic en **Compartir**.
   > [!WARNING]
   > Si no compartes la hoja con el correo de la cuenta de servicio como Editor, recibirás un error `403 Forbidden` cuando el backend intente escribir o leer datos.

---

## 2. Configuración de Zoom Webhooks (Origen de Datos)

Zoom necesita enviar notificaciones en tiempo real (webhooks) al backend cada vez que un participante interactúe con la sala de espera.

### Paso 2.1: Crear una App en el Zoom App Marketplace
1. Inicia sesión en el [Zoom App Marketplace](https://marketplace.zoom.us/) con tu cuenta de administrador u organizador de Zoom.
2. Haz clic en **Develop** (Desarrollar) en la parte superior derecha y selecciona **Build App** (Crear App).
3. Selecciona el tipo de App **Webhook Only** (Solo Webhook) y haz clic en **Create** (Crear).
4. Completa la información obligatoria de la App (Nombre, Compañía, Nombre de contacto y Correo).

### Paso 2.2: Obtener el Secret Token
1. En la pestaña **App Credentials** o **Feature** de la configuración de la app de Zoom, verás el campo **Secret Token** (Token Secreto).
2. Copia este valor y configúralo en el archivo `application.properties`:
   ```properties
   zoom.secret-token=TU_SECRET_TOKEN_DE_ZOOM
   ```
   *Este token se utiliza para calcular firmas HMAC-SHA256 y validar que las peticiones vengan genuinamente de Zoom.*

### Paso 2.3: Configurar URL y Suscribirse a Eventos
1. Activa la opción **Event Subscriptions** (Suscripciones a Eventos) en la configuración de la app de Zoom.
2. Añade una nueva suscripción:
   - **Event subscription name**: Registros de Sala de Espera.
   - **Event notification endpoint URL**: La dirección pública de tu servidor backend que expone el endpoint del webhook.
     - Para pruebas locales, debes usar un túnel como **ngrok**:
       ```bash
       ngrok http 8080
       ```
     - Copia la URL HTTPS de ngrok (ej: `https://abcd-123.ngrok-free.app`) y configúrala en Zoom como:
       `https://abcd-123.ngrok-free.app/api/webhooks/zoom`
3. Haz clic en **Add Events** (Añadir Eventos) y selecciona obligatoriamente los siguientes eventos bajo la categoría **Meeting** (Reunión):
   - `Participant joined waiting room` (El participante entró a la sala de espera)
   - `Participant left waiting room` (El participante salió o fue retirado de la sala de espera sin entrar)
   - `Participant joined meeting` (El participante fue admitido en la reunión principal)
4. Guarda los cambios.
5. **Validación de URL (CRC Check)**: Zoom requiere que la URL responda a un reto criptográfico antes de poder activar la suscripción. Al hacer clic en **Validate** en la consola de Zoom, esta enviará una petición de validación a tu backend.
   - Nuestro controlador [ZoomWebhookController](file:///c:/Users/franz%20schwartz/Documents/PROYECTOS-PERSONALES/zoomaccess/src/main/java/com/ZoomAccessDashboard/zoomaccess/infrastructure/input/rest/ZoomWebhookController.java) está preparado para responder automáticamente a este desafío de validación de forma exitosa y segura si el Secret Token está correctamente configurado.

---

## 3. Formato de la Hoja de Cálculo

El sistema escribe automáticamente las filas en la hoja con una estructura de **6 columnas**. Se recomienda crear la primera fila en tu Google Sheet con los siguientes títulos de cabecera:

| Columna A | Columna B | Columna C | Columna D | Columna E | Columna F |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Event Name** | **Meeting ID** | **Topic** | **Participant Name** | **Participant Email** | **Timestamp** |

El backend lee esta hoja y agrupa las filas por participante para reconstruir el estado actual, calculando el tiempo de espera y el estado final (`Admitted` o `Abandoned`) para presentarlo ordenado al frontend.

---

## 4. Ejecución del Backend

Para levantar el servicio de Spring Boot reactivo en puerto `8080`:
1. Asegúrate de tener instalado Java 21+.
2. Ejecuta el comando en tu terminal (en la raíz del proyecto):
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
3. El frontend de Angular (ejecutándose en `http://localhost:4200`) podrá hacer peticiones `GET` a `http://localhost:8080/api/v1/zoom/waiting-room-records` gracias a la configuración de CORS.
