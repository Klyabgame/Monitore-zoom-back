# Integración de Paginación en el Frontend

El endpoint de lectura de registros de sala de espera (`/api/v1/zoom/waiting-room-records`) ha sido actualizado para retornar los resultados paginados en lugar de un flujo plano continuo (`Flux`).

---

## 1. Cambios en la Solicitud (Request)

Ahora, el Frontend puede enviar los siguientes parámetros query opcionales en la URL para paginar el listado:

* **`page`**: El número de página a solicitar (basado en índice **0**). *Valor por defecto:* `0`.
* **`size`**: Cantidad de registros por página. *Valor por defecto:* `10`.

### URL de ejemplo con Filtros + Paginación:
```http
GET /api/v1/zoom/waiting-room-records?participantName=Franz&startDate=2026-05-01T00:00:00Z&endDate=2026-05-24T23:59:59Z&page=0&size=15
```

---

## 2. Nueva Estructura de la Respuesta (Response)

La respuesta ahora devuelve un objeto JSON estructurado con la información de paginación (`PageResponse`), con el siguiente formato:

```json
{
  "content": [
    {
      "meetingId": "123456789",
      "topic": "Clase de Programación Reactiva",
      "participantName": "Franz Schwartz",
      "email": "franz@example.com",
      "waitingRoomEntryTime": "2026-05-24T18:00:00.000Z",
      "exitTime": "2026-05-24T18:02:30.000Z",
      "waitingDurationSeconds": 150,
      "connectionStatus": "Admitted"
    }
  ],
  "totalElements": 48,
  "page": 0,
  "size": 15,
  "totalPages": 4
}
```

### Detalle de los Campos de Respuesta:
1. **`content`**: El array conteniendo el subconjunto (página) de registros de asistencia filtrados.
2. **`totalElements`**: Total acumulado de registros que coinciden con los filtros aplicados en el servidor (útil para mostrar leyendas como *"Mostrando 1-15 de 48 resultados"*).
3. **`page`**: El número de página actual que se está entregando (eco del parámetro enviado).
4. **`size`**: El tamaño máximo de la página (eco del parámetro enviado).
5. **`totalPages`**: El total de páginas calculadas automáticamente bajo la fórmula: $\lceil \text{totalElements} / \text{size} \rceil$.

---

## 3. Código Sugerido de Integración en el Frontend (Angular / TypeScript)

Dado que se observa en los metadatos que tienes `ng serve` corriendo, aquí tienes un ejemplo de cómo implementar tu servicio y componente en **Angular**:

### A. Definición del Modelo de Respuesta (`page-response.model.ts`)
```typescript
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface WaitingRoomRecord {
  meetingId: string;
  topic: string;
  participantName: string;
  email: string;
  waitingRoomEntryTime: string;
  exitTime: string | null;
  waitingDurationSeconds: number;
  connectionStatus: string;
}
```

### B. Servicio Angular (`attendance.service.ts`)
```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse, WaitingRoomRecord } from './page-response.model';

@Injectable({
  providedIn: 'root'
})
export class AttendanceService {
  private apiUrl = '/api/v1/zoom/waiting-room-records';

  constructor(private http: HttpClient) {}

  getWaitingRoomRecords(
    filters: { participantName?: string; startDate?: string; endDate?: string; status?: string },
    page: number = 0,
    size: number = 10
  ): Observable<PageResponse<WaitingRoomRecord>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (filters.participantName) {
      params = params.set('participantName', filters.participantName);
    }
    if (filters.startDate) {
      params = params.set('startDate', filters.startDate);
    }
    if (filters.endDate) {
      params = params.set('endDate', filters.endDate);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }

    return this.http.get<PageResponse<WaitingRoomRecord>>(this.apiUrl, { params });
  }
}
```

### C. Lógica del Componente (`attendance-list.component.ts`)
```typescript
import { Component, OnInit } from '@angular/core';
import { AttendanceService } from './attendance.service';
import { WaitingRoomRecord } from './page-response.model';

@Component({
  selector: 'app-attendance-list',
  templateUrl: './attendance-list.component.ts'
})
export class AttendanceListComponent implements OnInit {
  records: WaitingRoomRecord[] = [];
  
  // Paginación
  currentPage: number = 0;
  pageSize: number = 10;
  totalElements: number = 0;
  totalPages: number = 0;

  // Filtros
  filters = {
    participantName: '',
    startDate: '',
    endDate: '',
    status: 'all'
  };

  constructor(private attendanceService: AttendanceService) {}

  ngOnInit(): void {
    this.loadRecords();
  }

  loadRecords(): void {
    this.attendanceService.getWaitingRoomRecords(this.filters, this.currentPage, this.pageSize)
      .subscribe({
        next: (response) => {
          this.records = response.content;
          this.totalElements = response.totalElements;
          this.totalPages = response.totalPages;
          this.currentPage = response.page;
        },
        error: (err) => console.error('Error al cargar asistencia:', err)
      });
  }

  onPageChange(newPage: number): void {
    if (newPage >= 0 && newPage < this.totalPages) {
      this.currentPage = newPage;
      this.loadRecords();
    }
  }

  applyFilters(): void {
    this.currentPage = 0; // Reiniciar a la primera página tras filtrar
    this.loadRecords();
  }
}
```
