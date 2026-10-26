# Bitácora — Uruguay Mnatural (parte de Inés)

Registro de lo que se fue haciendo, las decisiones y los problemas que aparecieron.
Se completa a pedido: decile a Claude "agregá a la bitácora: …" y lo suma con la fecha.

## Mis PRs

| PR | Fecha de merge | Qué |
|---|---|---|
| #1 | 19/10/2026 | Consigna del Proyecto TIC (PDF) en el repo |
| #5 | 23/10/2026 | Registro de prestadores: repetir contraseña y RUT numérico de 12 dígitos |
| #6 | 23/10/2026 | Aprobación de prestadores por el administrador |
| #8 | 23/10/2026 | Seeder de prestadores de prueba |
| #9 | 23/10/2026 | Emails de aviso al prestador al aprobar o rechazar |
| #13 | 26/10/2026 | Tipos de actividad (ABM del admin) y selección múltiple para el prestador |
| #17 | 26/10/2026 | Panel del admin con prestadores por estado y alta de administradores |

## Entradas

### 05/10/2026 — Arranque
- Primer commit del proyecto. Entidades base: `User` (herencia JOINED), `Admin`, `Tourist`, `Provider`, y los enums `DocumentType` y `ApprovalStatus`.

### 15/10/2026 — Registro de prestadores
- Se leyó el diagrama UML y la especificación de requerimientos.
- Entidad `Business` (un prestador puede tener varios emprendimientos) y enum `LegalType`.
- Registro de prestadores con Thymeleaf: DTO con validaciones, `ProviderService`, controller y vistas. El prestador queda en estado PENDING.
- `SecurityConfig` provisoria (todo permitido, CSRF desactivado) hasta tener login.
- Página de inicio (`/`) con los dos botones de registro.

### 19/10/2026 — Ramas y reglas del equipo
- Se creó la estructura: `dev` y `feature/ines`, `feature/agus`, `feature/carlota`, `feature/camila`. Nadie pushea a `master` ni a `dev`: se trabaja en la rama propia y se abre PR hacia `dev`.
- Camila sacó `.idea` del repo; hubo que sincronizar y restaurar la carpeta local.
- PR #1 (consigna) y `CLAUDE.md` con las reglas del equipo (PR #2 de Camila).

### 23/10/2026 — Registro, aprobación, seeder y emails
- **#5:** "Repetir contraseña" en el registro. El RUT pasó de `String` a `Long` (12 dígitos). Hubo que correr `ALTER TABLE business ALTER COLUMN rut TYPE bigint USING rut::bigint;` en las bases ya creadas.
- **#6:** pantalla `/admin/prestadores` (pendientes por orden de llegada, aprobar y rechazar con motivo). `Provider` suma `requestedAt`, `reviewedAt`, `reviewedBy` y `rejectionReason`. `requestedAt` lleva `default now()` en la base para poder agregar la columna con filas existentes.
- **#8:** `TestProviderSeeder` crea un prestador aprobado, uno rechazado y uno pendiente. Tiene un TODO para sacarlo antes de entregar.
- **#9:** emails de aviso. `EmailService` con el correo desactivado por defecto (imprime el mensaje en el log), sin cuentas ni contraseñas en el repo, y el correo sale solo después del commit de la transacción.
- Lo de Agustina (#3 login con roles y registro de turista) y Camila (#4 y #7 actividades) entró el mismo día.

### 26/10/2026 — Tipos de actividad, panel del admin y admins
- **#13:** el admin administra los tipos de actividad (agregar, renombrar, activar/desactivar, borrar si no tienen actividades). El nombre no se repite ignorando mayúsculas y tildes. El prestador elige uno o más tipos con botones de selección múltiple (la relación sigue siendo muchos a muchos). No se cargan tipos de antemano.
- **#17:**
  - El admin ve los prestadores pendientes, aprobados y rechazados, y al iniciar sesión va directo a ese panel (cambio en `SecurityConfig`).
  - "Agregar admin": un admin crea a otros. El usuario es el correo. La contraseña la genera el sistema al azar y se manda solo por correo; botón "Enviar nueva contraseña".
  - `AdminSeeder` crea el primer admin solo si no hay ninguno, con la contraseña de `ADMIN_PASSWORD` (si falta, genera una y la muestra una vez en el log).

## Decisiones
- El **usuario** de todos es el correo (el login no tiene un campo "usuario" aparte).
- La contraseña de un admin nuevo no la elige nadie: **al azar y solo por correo**.
- Con el correo desactivado (desarrollo), la contraseña generada queda **impresa en el log**; con el correo activado nunca se escribe en el log.
- El email no puede estar usado por ningún usuario del sistema (turistas, prestadores y admins).
- Un tipo de actividad con actividades no se borra: se desactiva.
- Los tests que usan la base real pueden dejar datos si fallan: no correr `BookingServiceTest` contra la base de uso diario.

## Incidencias
- **Columna `available_spots` (26/10):** Camila sacó `availableSpots` de `ActivitySchedule`, pero en las bases ya creadas la columna seguía como obligatoria y fallaba crear horarios. Se borró de la base local con `ALTER TABLE activity_schedule DROP COLUMN available_spots;` y se verificó que el esquema quedó igual al de una base nueva.
- **Dos PostgreSQL en la misma compu (26/10):** la app usa el PostgreSQL instalado en Windows (puerto 5432); el contenedor Docker `mnatural-db` tiene una base vacía. El comando de Camila (`docker exec …`) apuntaba al contenedor, no a la base real.
- **Basura de un test (26/10):** una corrida fallida de `BookingServiceTest` dejó 5 prestadores `prestador-…@test.uy` con su emprendimiento, tipo y actividad en la base real. Se borraron por id exacto.
- **Contraseña vieja del admin:** el valor por defecto que estaba en `AdminSeeder` sigue en el historial de Git, aunque ya no está en el código. Los admins creados con él la conservan.

## Variables de entorno
| Variable | Para qué |
|---|---|
| `ADMIN_PASSWORD` | Contraseña del primer admin (si falta, se genera y se muestra una vez en el log) |
| `ADMIN_EMAIL` | Correo del primer admin (por defecto `admin@mnatural.uy`) |
| `MAIL_ENABLED` | `true` para enviar correos de verdad (por defecto `false`) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Servidor de correo |
| `APP_BASE_URL` | Dirección pública para los links de los correos (por defecto `http://localhost:8080`) |

## Pendientes
- Sacar `AdminSeeder` y `TestProviderSeeder` (o cambiar sus datos) antes de entregar.
- Idioma por país al registrarse: hay un TODO en `TouristService` y `ProviderService`.
- No existe cambiar la propia contraseña ni desactivar o borrar admins.
- Actividades: falta editar, suspender y borrar desde el espacio del prestador.
- Pagos: Camila ya empezó la fase 1 (#15).
