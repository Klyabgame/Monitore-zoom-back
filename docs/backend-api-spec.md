# Especificación de API para Backend - Reporte de Sala de Espera de Zoom

Este documento detalla la estructura, parámetros y formatos de respuesta requeridos por el Frontend (desarrollado bajo Arquitectura Hexagonal y Angular 21) para el panel de control de salas de espera de Zoom (CEFOESP SEGURIDAD SUPERIOR SAC).

---

## 📌 Endpoint Principal

### `GET /api/v1/zoom/waiting-room-records`

Retorna el listado filtrado de personas que ingresaron o intentaron ingresar a la sala de espera de Zoom de la organización.

- **Método HTTP:** `GET`
- **Formato de Respuesta:** `application/json`

---

## 🔍 Parámetros de Búsqueda (Query Parameters)

Para dar soporte al componente de filtros del frontend (`access-filters.component`), el backend debe soportar los siguientes parámetros opcionales en la consulta HTTP:

| Parámetro | Tipo | Formato / Valores | Descripción |
| :--- | :--- | :--- | :--- |
| `participantName` | `string` | Texto libre (ej: `Franz`) | Filtra registros que contengan el valor (búsqueda parcial insensible a mayúsculas) en: `participantName`, `email`, o `topic`. |
| `startDate` | `string` | ISO 8601 (ej: `2026-05-22T08:00:00`) | Filtra registros cuya fecha/hora de ingreso (`waitingRoomEntryTime`) sea **mayor o igual** a este valor. |
| `endDate` | `string` | ISO 8601 (ej: `2026-05-22T18:00:00`) | Filtra registros cuya fecha/hora de ingreso (`waitingRoomEntryTime`) sea **menor o igual** a este valor. |
| `status` | `string` | `Admitted` \| `Abandoned` | Filtra según el estado de la conexión. Si se omite o es `All`, retorna todos. |

---

## 📦 Formato del Objeto de Dominio (Response Schema)

La respuesta del servidor debe ser un **Array JSON** de objetos. Cada objeto debe seguir estrictamente el modelo de datos `ZoomAccessRecord` del dominio:

```json
[
  {
    "meetingId": "849 2038 9102",
    "topic": "Inducción de Seguridad - Planta Norte",
    "participantName": "Franz Schwartz",
    "email": "fschwartz@cefoesp.com",
    "waitingRoomEntryTime": "2026-05-22T10:15:30.000Z",
    "exitTime": "2026-05-22T10:17:34.000Z",
    "waitingDurationSeconds": 124,
    "connectionStatus": "Admitted"
  },
  {
    "meetingId": "849 2038 9102",
    "topic": "Inducción de Seguridad - Planta Norte",
    "participantName": "Carlos Mendoza",
    "email": "cmendoza.guest@gmail.com",
    "waitingRoomEntryTime": "2026-05-22T10:22:15.000Z",
    "exitTime": "2026-05-22T10:29:15.000Z",
    "waitingDurationSeconds": 420,
    "connectionStatus": "Abandoned"
  }
]
```

### Detalle de Campos Obligatorios

| Campo | Tipo | Nullable | Descripción |
| :--- | :--- | :--- | :--- |
| `meetingId` | `string` | No | ID único formateado de la reunión de Zoom (ej: `123 4567 8901`). |
| `topic` | `string` | No | Tema o título asignado a la reunión. |
| `participantName` | `string` | No | Nombre del participante registrado en Zoom. |
| `email` | `string` | No | Correo electrónico ingresado por el participante (o asociado a su cuenta). |
| `waitingRoomEntryTime` | `string` | No | Fecha y hora en formato ISO 8601 (UTC) cuando ingresó a la sala de espera. |
| `exitTime` | `string` | **Sí** | Fecha y hora en formato ISO 8601 (UTC) cuando salió (admitido o retirado). `null` si aún está en espera. |
| `waitingDurationSeconds` | `number` | No | Segundos acumulados que el participante estuvo en la sala de espera (`exitTime` - `waitingRoomEntryTime`). |
| `connectionStatus` | `string` | No | Estado final del participante. Debe ser exactamente: `Admitted` (Admitido a la reunión) o `Abandoned` (Abandonó la sala de espera antes de ser admitido). |

---

## 🚦 Respuestas HTTP Esperadas

### 🟢 `200 OK`
La consulta se ejecutó con éxito. Retorna la lista filtrada de registros (incluso si la lista está vacía `[]`).

### 🟡 `400 Bad Request`
Se enviaron parámetros inválidos (ej. fechas mal formadas).
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "El parámetro 'startDate' debe estar en formato ISO 8601 válido."
}
```

### 🔴 `500 Internal Server Error`
Error de comunicación con las APIs de Zoom u otro fallo interno del servidor.
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Error temporal al conectar con la API de Zoom."
}
```

---

## ⚡ Consideraciones Técnicas para el Backend

1. **CORS:** El servidor backend debe habilitar CORS para el origen del frontend en desarrollo (usualmente `http://localhost:4200`) y producción.
2. **Paginación (Opcional):** Si el volumen de datos aumenta considerablemente, se recomienda soportar los parámetros `page` y `limit` en el futuro, manteniendo la respuesta de lista para consultas simples.
3. **Caché:** Como la sala de espera es en tiempo real pero requiere reportes rápidos, se sugiere cachear las respuestas de reuniones pasadas o cerradas por al menos 5 minutos, y realizar llamadas calientes para reuniones activas.
