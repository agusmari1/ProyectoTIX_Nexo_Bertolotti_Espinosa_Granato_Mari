# mNatural — Proyecto TIC (equipo Nexo)

Spring Boot + Thymeleaf + PostgreSQL (con Docker). Esta carpeta es la raíz del proyecto (`pom.xml`, `docker-compose.yml`).

Trabajo con Camila, que usa la rama `feature/camila`. Es principiante: explicar brevemente cada paso que se hace.

## Reglas de Git

- Nadie pushea directo a `master` ni a `dev`. Cada una trabaja en su rama (`feature/<nombre>`) y después hace pull request hacia `dev`.
- `master` queda intacta y solo recibe de `dev` cuando se entrega o se le muestra al docente.
- Antes de empezar cada día: checkout a `dev`, `git pull`, volver a `feature/camila` y mergear `dev` adentro.
- Cuando algo funciona: push de la rama, pull request en GitHub con base `dev`, que otra compañera lo revise y apruebe, y recién ahí merge.
- Antes de cualquier commit o push, verificar con `git branch --show-current` que la rama es `feature/camila`. Si es `master` o `dev`, frenar y avisar.
- No hacer commits ni push sin que Camila lo pida explícitamente.
- Antes de commitear, mostrar la lista de archivos. Nunca incluir archivos de `.idea` ni `.env`.

## Archivos compartidos

Avisar antes de tocarlos, porque afectan a todo el equipo:

- `User.java`
- `SecurityConfig.java`
- `application.properties`
- `pom.xml`
- El layout común de las vistas

## Reparto del equipo

- **Inés:** administración de prestadores. Pantalla del admin, listado de pendientes por orden de llegada, aprobar y rechazar. Agrega a `Provider` los campos `reviewedBy`, `reviewedAt` y `rejectionReason`.
- **Camila:** actividades. Entidades `Activity`, `ActivityType` y `ActivitySchedule`. Alta de actividad desde el espacio del prestador, con tipos, precio, duración, edad mínima, política de cancelación y límite de reserva. Es la rama más grande.
- **Agustina:** login y seguridad. Roles para los tres tipos de usuario, y que el prestador no entre si no está aprobado. Ella toca `SecurityConfig`.
- **Carlota:** portal público. Listado de actividades sin login, filtros por departamento y tipo, y pantalla de detalle.
