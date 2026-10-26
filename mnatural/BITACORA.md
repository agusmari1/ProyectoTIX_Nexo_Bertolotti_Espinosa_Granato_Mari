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

## Detalle: tipos de actividad y administradores (26/10/2026)

### Tipos de actividad — `/admin/tipos-actividad` (solo ADMIN)
- **Qué se puede hacer:** agregar, renombrar, activar/desactivar y borrar. La lista muestra cuántas actividades usa cada tipo.
- **Reglas:**
  - El nombre es obligatorio y no se puede repetir, sin distinguir mayúsculas, tildes ni espacios de más ("senderismo", "Senderismó" y "SENDERISMO" cuentan como el mismo).
  - Un tipo con actividades no se borra (aparece un aviso y el botón queda deshabilitado): se desactiva.
  - Un tipo desactivado no se ofrece para actividades nuevas, pero las que ya lo tienen lo conservan.
  - No hay tipos cargados de antemano. Hasta que el admin cargue uno, ningún prestador puede crear actividades.
- **Lado del prestador:** en `/provider/activities/new` los tipos activos son botones de selección múltiple (uno o más). Si no elige ninguno, el formulario muestra el error "Elegí al menos un tipo de actividad.".
- **Archivos:** `AdminActivityTypeController`, `ActivityTypeService`, `ActivityTypeDTO`, `ActivityTypeException` y `ActivityTypeNotFoundException`, vista `admin/tipos-actividad.html`, dos consultas nuevas en `ActivityTypeRepository` y el bloque de tipos de `provider/activities/form.html` (de Camila).
- **Detalle técnico:** los checkboxes del formulario no usan `th:field`, porque agrega un input oculto entre el checkbox y su botón y Bootstrap deja de pintar el botón marcado.
- **Tests:** `ActivityTypeServiceTest` (11).

### Administradores — `/admin/administradores` (solo ADMIN)
- **Qué se puede hacer:** ver la lista de admins, crear uno (nombre, apellido, fecha de nacimiento y correo) y generarle una contraseña nueva a uno existente.
- **Reglas:**
  - El usuario del admin nuevo es su correo. No puede estar usado por ningún usuario del sistema y tiene que tener el formato `algo@algo.algo` (el mismo que exige el login).
  - La contraseña (16 caracteres) la genera el sistema al azar, se guarda con BCrypt y se manda solo por correo; quien lo crea no la ve.
  - "Enviar nueva contraseña" genera otra y la anterior deja de servir. Sirve si el primer correo no llegó.
  - Tipo y número de documento del admin nuevo quedan con valores de relleno (`OTHER` / `00000000`).
- **Panel principal:** al iniciar sesión el admin va a `/admin/prestadores`, con prestadores pendientes, aprobados y rechazados y un menú hacia "Tipos de actividad" y "Agregar admin".
- **Seguridad:** `/admin/**` exige el rol ADMIN (un prestador recibe 403; sin sesión se redirige al login).
- **Correo:** con `app.mail.enabled=false` no sale ningún mail y la contraseña generada queda impresa en el log. Con el correo activado nunca se escribe en el log. Si el envío falla, el admin queda creado igual.
- **Archivos:** `AdminAccountController`, `AdminAccountService`, `AdminRegistrationDTO`, `AdminCreatedEvent`, `AdminPasswordResetEvent` y `AdminCreatedNotifier`, `PasswordGenerator`, `AdminNotFoundException`, dos correos nuevos en `EmailService`, `AdminSeeder` y la vista `admin/administradores.html`. En `SecurityConfig` se agregó la redirección del admin al panel.
- **Tests:** `AdminAccountServiceTest` (5), `PasswordGeneratorTest` (2), `AdminSeederTest` (4) y los correos nuevos en `EmailServiceTest`.

### Cómo probarlo
1. Entrar como admin (si no se definió `ADMIN_PASSWORD`, la contraseña inicial está en el log del primer arranque).
2. *Tipos de actividad*: agregar "Senderismo" y "Gastronomía"; probar agregar "senderismo" (lo rechaza).
3. Entrar como prestador aprobado → *Nueva actividad*: elegir dos tipos y guardar; probar enviarlo sin tipos.
4. *Agregar admin*: crear uno, buscar su contraseña en el log (con el correo desactivado), entrar con ese correo y esa contraseña, y probar *Enviar nueva contraseña*.

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
