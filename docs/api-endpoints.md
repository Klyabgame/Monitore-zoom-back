# ZoomAccess API Endpoints

Este documento describe los endpoints disponibles en el backend de ZoomAccess para ser consumidos por la aplicación frontend (Angular).

---

## 1. Obtener Registros de Sala de Espera (Waiting Room Records)

Este endpoint devuelve la lista agregada y procesada de los participantes que han pasado por la sala de espera, junto con su tiempo de espera y estado final (`Admitted`, `Abandoned`, `Waiting`).

*   **URL:** `/api/v1/zoom/waiting-room-records`
*   **Method:** `GET`
*   **Content-Type:** `application/json`

### Query Parameters (Filtros Opcionales)

Todos los parámetros son opcionales. Puedes combinarlos para realizar búsquedas específicas.

| Parámetro | Tipo | Descripción | Ejemplo |
| :--- | :--- | :--- | :--- |
| `participantName` | `string` | Término de búsqueda parcial (case-insensitive). Busca en el nombre del participante, correo electrónico o tema de la reunión. | `?participantName=juan` |
| `startDate` | `string` | Fecha de inicio para filtrar los registros. Formato ISO-8601. | `?startDate=2026-05-22T00:00:00Z` |
| `endDate` | `string` | Fecha de fin para filtrar los registros. Formato ISO-8601. | `?endDate=2026-05-23T00:00:00Z` |
| `status` | `string` | Filtra por el estado final de la conexión (`Admitted`, `Abandoned`, `Waiting`, `all`). | `?status=Admitted` |

### Respuesta Exitosa (200 OK)

Devuelve un arreglo JSON (generado de forma reactiva) con la estructura `WaitingRoomRecordResponse`.

**Ejemplo de Respuesta:**

```json
[
  {
    "meetingId": "12345678901",
    "topic": "Reunión de Sincronización",
    "participantName": "Franz Schwartz",
    "email": "franz@example.com",
    "waitingRoomEntryTime": "2026-05-22T16:00:00Z",
    "waitingRoomExitTime": "2026-05-22T16:05:00Z",
    "durationSeconds": 300,
    "connectionStatus": "Admitted"
  },
  {
    "meetingId": "12345678901",
    "topic": "Reunión de Sincronización",
    "participantName": "Usuario Desconocido",
    "email": "",
    "waitingRoomEntryTime": "2026-05-22T16:10:00Z",
    "waitingRoomExitTime": null,
    "durationSeconds": 0,
    "connectionStatus": "Abandoned"
  }
]
```

---

## (Interno) Endpoint para el Webhook de Zoom

*Nota: Este endpoint es consumido exclusivamente por los servidores de Zoom, no por el frontend.*

*   **URL:** `/api/webhooks/zoom`
*   **Method:** `POST`
*   **Headers Requeridos:** `x-zm-signature`, `x-zm-request-timestamp`
