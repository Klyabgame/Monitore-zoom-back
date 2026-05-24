# Guía de Pruebas: Verificación de Backpressure y Procesamiento por Lotes (Batching)

Esta guía explica cómo probar la resiliencia y el comportamiento del buffer de backpressure implementado con `Sinks.Many` y `bufferTimeout`.

---

## 1. Concepto de Verificación mediante Logs

Al disparar ráfagas concurrentes de peticiones (por ejemplo, 45 estudiantes ingresando en simultáneo), el flujo reactivo debe comportarse de la siguiente manera:

1. **Recepción Inmediata (Controlador)**: 
   El controlador recibirá las peticiones, las validará y las meterá en el sumidero (`webhookSink`) retornando inmediatamente un `200 OK` a Zoom. Verás logs continuos como este por cada petición:
   ```text
   INFO  c.Z.z.i.i.rest.ZoomWebhookController : Received Zoom webhook request. Signature: ..., Timestamp: ...
   DEBUG c.Z.z.i.i.rest.ZoomWebhookController : Queueing record in reactive sink: ZoomAccessRecord[...]
   ```

2. **Agrupamiento en Lotes (Buffer)**:
   El buffer acumulará los registros. Dado que configuramos `.bufferTimeout(20, Duration.ofSeconds(2))`, verás que el procesador de fondo se activa únicamente cuando se acumulan **20 registros** o cuando transcurren **2 segundos**. Verás estos logs de procesamiento en lotes:
   ```text
   INFO  c.Z.z.i.i.rest.ZoomWebhookController : Processing buffered batch of 20 ZoomAccessRecords
   INFO  c.Z.z.a.s.ZoomAccessService          : Processing Zoom webhook batch of size 20 in application layer
   INFO  c.Z.z.i.o.g.GoogleSheetsAdapter      : Successfully appended batch of 20 rows to spreadsheet
   ```

   Para una ráfaga de 45 peticiones, deberías ver logs correspondientes a:
   * **Lote 1**: 20 registros insertados de un solo golpe.
   * **Lote 2**: 20 registros insertados de un solo golpe.
   * **Lote 3**: 5 registros restantes insertados (al expirar el tiempo de espera de 2 segundos).

---

## 2. Cómo realizar Pruebas Manuales con `curl`

Debido a que la aplicación tiene validación de firmas activa, para enviar peticiones manuales con `curl` tienes dos opciones:

### Opción A: Usar el Script Automatizado (Recomendado)
Usa el script `stress-test.sh` provisto en la raíz del proyecto. Este script calcula automáticamente la firma HMAC-SHA256 usando OpenSSL y la inyecta en las cabeceras correspondientes, simulando ser el servidor de Zoom real.

### Opción B: Desactivar Temporalmente la Firma (Solo para Desarrollo)
Si quieres enviar peticiones directas de `curl` sin calcular firmas:
1. Ve a [ZoomSignatureValidator.java](file:///c:/Users/franz%20schwartz/Documents/PROYECTOS-PERSONALES/zoomaccess/src/main/java/com/ZoomAccessDashboard/zoomaccess/infrastructure/security/ZoomSignatureValidator.java).
2. Modifica el inicio del método `validate` para retornar el payload inmediatamente:
   ```java
   public Mono<String> validate(String payload, String signatureHeader, String timestampHeader) {
       return Mono.just(payload); // <-- Agrega esta línea temporalmente para bypass
   }
   ```
3. Ejecuta los siguientes comandos `curl` en tu terminal:

#### Comando para simular: Participante en Sala de Espera
```bash
curl -X POST http://localhost:8080/api/webhooks/zoom \
  -H "Content-Type: application/json" \
  -H "x-zm-signature: bypass-signature" \
  -H "x-zm-request-timestamp: 1600000000" \
  -d '{
    "event": "meeting.participant_joined_waiting_room",
    "event_ts": '$(date +%s)',
    "payload": {
      "account_id": "zoom_acc_123",
      "object": {
        "id": "1234567890",
        "topic": "Clase de Prueba Reactiva",
        "participant": {
          "user_id": "part_999",
          "user_name": "Estudiante Pruebas Waiting",
          "email": "estudiante_waiting@test.com"
        }
      }
    }
  }'
```

#### Comando para simular: Participante Admitido (Ingresa a la reunión)
```bash
curl -X POST http://localhost:8080/api/webhooks/zoom \
  -H "Content-Type: application/json" \
  -H "x-zm-signature: bypass-signature" \
  -H "x-zm-request-timestamp: 1600000000" \
  -d '{
    "event": "meeting.participant_joined",
    "event_ts": '$(date +%s)',
    "payload": {
      "account_id": "zoom_acc_123",
      "object": {
        "id": "1234567890",
        "topic": "Clase de Prueba Reactiva",
        "participant": {
          "user_id": "part_999",
          "user_name": "Estudiante Pruebas Admitted",
          "email": "estudiante_admitted@test.com"
        }
      }
    }
  }'
```
