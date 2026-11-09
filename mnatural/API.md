# API pública de Uruguay Mnatural

API REST de solo lectura para que otras plataformas consulten la oferta turística (`ESPECIFICACION.md`, sección 9).

- **URL base (local):** `http://localhost:8080/api`
- **Autenticación:** no hace falta.
- **Métodos:** solo `GET`.
- **Formato:** JSON. Las fechas usan el formato `2026-11-20` y las fechas con hora, `2026-11-20T10:00:00`.
- **Qué muestra:** solo actividades publicadas y emprendimientos de prestadores aprobados. Nunca muestra contraseñas, correos ni teléfonos.

Valores de `department`: `ARTIGAS`, `CANELONES`, `CERRO_LARGO`, `COLONIA`, `DURAZNO`, `FLORES`, `FLORIDA`, `LAVALLEJA`, `MALDONADO`, `MONTEVIDEO`, `PAYSANDU`, `RIO_NEGRO`, `RIVERA`, `ROCHA`, `SALTO`, `SAN_JOSE`, `SORIANO`, `TACUAREMBO`, `TREINTA_Y_TRES`.

## Errores

Un pedido inválido responde `400 Bad Request` con este formato:

```json
{ "error": "Falta el parámetro obligatorio 'date'." }
```

## Rutas

### 1. Listado de emprendimientos

`GET /api/businesses`

Devuelve los emprendimientos de prestadores aprobados, ordenados por nombre.

```json
[
  {
    "id": 1,
    "name": "Emprendimiento Aprobado",
    "legalType": "INDIVIDUAL",
    "servicesDescription": null
  }
]
```

### 2. Verificar el estado de un emprendimiento

`GET /api/businesses/{id}/status`

Indica si el emprendimiento existe y si su prestador está aprobado. Siempre responde `200`, también cuando no existe. El nombre solo aparece si está aprobado.

| Caso | Respuesta |
|---|---|
| Aprobado | `{ "exists": true, "providerApproved": true, "name": "Emprendimiento Aprobado" }` |
| Existe, pero no está aprobado | `{ "exists": true, "providerApproved": false }` |
| No existe | `{ "exists": false, "providerApproved": false }` |

### 3. Actividades disponibles en un día

`GET /api/activities/available?date=2026-11-20&department=LAVALLEJA`

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `date` | Sí | Día a consultar |
| `department` | Uno de los dos | Departamento |
| `locality` | Uno de los dos | Localidad, o parte de su nombre. No distingue mayúsculas ni tildes |

Si se indican `department` y `locality`, se aplican los dos filtros.

Un horario está disponible si no está suspendido, si todavía se puede reservar según el límite de minutos del prestador y si le quedan lugares. Las actividades sin horarios disponibles ese día no aparecen.

```json
[
  {
    "activity": {
      "id": 1,
      "name": "Cabalgata en las sierras",
      "description": "Recorrido a caballo por las sierras de Minas.",
      "department": "LAVALLEJA",
      "locality": "Minas",
      "address": "Plaza principal",
      "durationMinutes": 120,
      "pricePerPerson": 1500.00,
      "minAge": 12,
      "requirements": null,
      "equipment": null,
      "businessName": "Emprendimiento Aprobado",
      "activityTypes": ["Cabalgata"]
    },
    "schedules": [
      { "id": 5, "startDateTime": "2026-11-20T10:00:00", "availableSpots": 8 }
    ]
  }
]
```

### 4. Top 3 de actividades del mes por departamento

`GET /api/activities/top?department=ROCHA`

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `department` | Sí | Departamento |

> **Estado actual:** las reseñas todavía no están programadas, así que esta ruta devuelve siempre una lista vacía (`[]`).

Cuando existan las reseñas, va a devolver hasta 3 actividades ordenadas por el promedio de las reseñas publicadas en el mes calendario en curso. Los empates se resuelven por mayor cantidad de valoraciones.

```json
[
  {
    "activity": { "id": 1, "name": "Cabalgata en las sierras" },
    "averageRating": 4.8,
    "ratingCount": 12
  }
]
```

En este ejemplo, `activity` está resumido: tiene los mismos campos que en la ruta 3.

### 5. Buscar actividades por palabra clave

`GET /api/activities/search?q=cabalgata`

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `q` | Sí | Texto a buscar, de hasta 100 caracteres |

Busca en el nombre, la descripción, la localidad y los tipos de actividad, sin distinguir mayúsculas ni tildes. Devuelve una lista de actividades ordenadas por nombre, con los mismos campos que `activity` en la ruta 3.
