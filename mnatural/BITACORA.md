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
| #26 | 09/11/2026 | Espacio del prestador: emprendimientos, editar/suspender actividad, reservas, verificar código, clima y cambio de contraseña |
| #32 | 09/11/2026 | Pagos: webhook de Mercado Pago y reembolso de reservas vencidas; fotos y actividades de demostración |
| #33 | 09/11/2026 | Foto propia para cada actividad de demostración y créditos de las fotos |
| #34 | 09/11/2026 | Pagos: formulario de tarjeta dentro de la aplicación (Mercado Pago o simulado) |
| #35 | 10/11/2026 | Correos automáticos del sistema y Mailpit para probarlos |

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

### 09/11/2026 — Espacio del prestador, clima y cuenta (#26)
- **Mis emprendimientos** (`/provider/businesses`): listar, editar y agregar, con las validaciones del registro. `ApprovedProviderResolver` centraliza "solo prestador aprobado". Menú compartido en `fragments/provider-nav`.
- **Editar y suspender actividad:** `ActivityService` suma `update` y `suspendActivity` (cambio mínimo en archivo de Camila). Suspender reutiliza `suspendSchedule` (reembolso 100 %); el portal y la API ya filtraban por `PUBLISHED`. **Republicar no se hizo:** falta saber si hay reembolsos pendientes.
- **Reservas** (`/provider/reservations`): repositorio propio `ProviderBookingRepository`. Contacto y participantes solo en reservas confirmadas (especificación, secciones 4 y 6).
- **Verificar código** (`/provider/verificar`): válido si es del prestador y está confirmada; inexistente y ajeno dan el mismo mensaje.
- **Clima** (Open-Meteo, sin clave): paquete `weather`, una línea de include en `actividades/detail.html`, memoria de 30 min, nunca rompe el detalle.
- **Configuración:** `TestProviderSeeder` solo con `app.seed.test-providers=true`; `show-sql` apagado por defecto; `/cuenta/contrasena` para cambiar la propia contraseña.
- **Tests:** de 160 a 321 (mocks, `@WebMvcTest` y servidor HTTP falso; sin base real ni internet).
- **Incidencia:** el `JAVA_HOME` del sistema apunta a un `.zip`; `mvnw` falla en PowerShell. Se corrió con el JDK de `Downloads`.
- **Plan para la contraseña de la base (sin aplicar):** `.env` en `.gitignore` (hoy no está), `.env.example`, `${DB_PASSWORD}` en `docker-compose.yml` y `application.properties`. La contraseña actual ya está en el historial de Git.

### 09/11/2026 — Pagos, fotos y actividades de demostración (#32, #33, #34)
- **Webhook de Mercado Pago** (`POST /payments/webhook`): con el id del aviso se consulta el pago a Mercado Pago (no se le cree al body), es idempotente y contesta 500 si Mercado Pago no responde para que reenvíe. `notification_url` solo con `https` (ngrok). Regla en `SecurityConfig` solo para esa ruta.
- **Arreglos al flujo de pagos que ya existía:** una reserva vencida con pago aprobado no se reembolsaba aunque la pantalla lo prometía (ahora se reembolsa el 100 %, solo reservas vencidas) y un intento rechazado que luego se aprobaba en la misma página de pago quedaba rechazado para siempre.
- **Formulario de tarjeta dentro de la app** (`/payments/bookings/{id}/pay`): con `MP_PUBLIC_KEY` es el de Mercado Pago (Card Payment Brick, la tarjeta se convierte en un token en el navegador); con el pago simulado, uno propio de prueba donde el nombre del titular elige el resultado (`APRO`, `OTHE`, `FUND`, `CONT`). Nunca se guarda el número ni el CVV, solo marca y últimos 4 dígitos.
- **Actividades de demostración:** `app.seed.demo-activities=true` crea 15 actividades (3 por departamento) con horarios futuros y capacidades distintas, cada una con su foto del lugar (Wikimedia Commons, créditos en `static/img/actividades/CREDITOS.md`).
- **Incidencia:** `PublicActivityControllerTest` (Agustina) fallaba en `dev` al combinarse con el clima del #26; el clima ahora se muestra solo si el servicio existe.
- **Incidencia:** Docker Desktop no abría porque quedaron procesos colgados. La base de datos corre dentro de Docker (puerto 5432): se hizo una copia de seguridad antes de reiniciarlo y no se perdió nada.

### 10/11/2026 — Correos automáticos y Mailpit (#35)
- **Tecnología:** Spring Boot Mail (Jakarta Mail) por **SMTP**, con `JavaMailSender` y `SimpleMailMessage`; el correo con QR adjunto usa `MimeMessageHelper` y el QR se genera con ZXing (Camila). No se usa una API externa de correo: se manda a un servidor SMTP configurable. En desarrollo ese servidor es **Mailpit** (Docker, http://localhost:8025).
- **Cómo funciona:** el servicio que hace la operación publica un evento (`ApplicationEventPublisher`) dentro de la transacción; un listener `@TransactionalEventListener(phase = AFTER_COMMIT)` arma el mensaje y `EmailService.send(...)` lo manda cuando la operación ya quedó guardada. Con `MAIL_ENABLED=false` no se manda nada y el mensaje se escribe en el log.
- **Regla del diseño:** si el correo falla, solo se registra en el log; nunca se deshace una reserva, un pago ni un registro. Ningún correo lleva contraseñas (salvo la inicial de un admin nuevo) ni datos de otros usuarios.
- **Correos:**
  - Turista: cuenta creada, reserva pendiente de pago con plazo, pago rechazado o con error, reserva confirmada con QR (Camila), reserva vencida, cancelada por el turista con reembolso, cancelada por suspensión (100 %), reembolso completado.
  - Prestador: solicitud recibida, aprobada o rechazada (ya existía), nueva reserva confirmada, un turista canceló.
  - Administrador: cuenta creada con contraseña generada (ya existía), nuevo prestador pendiente.
- **Decisiones:** no hay un correo aparte de "pago aprobado" (el de reserva confirmada ya dice que el pago se acreditó y trae el resumen); el pago rechazado se avisa solo cuando el estado cambia, para no repetirlo; las reservas vencidas se avisan en dos pasos (se anotan las por vencer, se vencen y se avisa solo de las realmente vencidas); el correo al prestador no lleva email ni teléfono del turista (los ve en su panel, que pide sesión).
- **Dónde está** (`src/main/java/uy/edu/um/nexo/mnatural/`): `service/EmailService` (envío y textos), `service/EmailEvents` (eventos), `service/EmailNotifier` (listeners), `service/BookingExpiryNotifier` y `BookingExpirationTask` (vencimientos), `repository/BookingNotificationRepository`, `mail/SmtpBookingMailer` y `LoggingBookingMailer` (QR, de Camila); más `ProviderReviewNotifier`, `AdminCreatedNotifier` y `BookingConfirmationNotifier`, que ya existían. Configuración en `application.properties` y `docker-compose.yml`; tests en `src/test/java/.../service/` (59 nuevos de correos, 507 en total).
- **Cómo se probó:** tests automáticos y, con la app real, registro, reserva, pago rechazado y aprobado, cancelación, suspensión y vencimiento; llegaron los correos esperados a Mailpit con remitente, textos y montos correctos.
- **Incidencia:** con `MAIL_USERNAME` vacío el correo del QR salía sin remitente; ahora todos usan `app.mail.from`.

### 11/11/2026 — Reseñas de actividades
- **Qué es:** el turista califica una actividad de 1 a 5 (con comentario opcional) y el detalle público `/actividades/{id}` muestra el promedio, la cantidad y la lista de reseñas visibles.
- **Reglas:** solo el titular de una reserva **CONFIRMADA**, y recién cuando la actividad **terminó** (horario de inicio + duración de la actividad); **una reseña por reserva**. Si todavía no terminó, el turista vuelve a "Mis reservas" con el mensaje de cuándo va a poder calificar.
- **Eliminar:** el turista puede eliminar la suya, pero **no se borra de la base**: pasa a estado `DELETED` (con fecha), deja de mostrarse y de contar para el promedio. Como hay una sola reseña por reserva, después de eliminarla no puede dejar otra por esa misma reserva.
- **Respuesta del prestador:** puede responder públicamente (una sola vez, hasta 1000 caracteres) desde el menú **Reseñas** (`/provider/reviews`). No hay ninguna ruta para que el prestador elimine reseñas (probado: 404).
- **Dónde está** (`src/main/java/uy/edu/um/nexo/mnatural/`): `entities/Review` y `ReviewStatus`, `repository/ReviewRepository` (consultas del promedio y las listas), `service/ReviewService` (todas las reglas), `controllers/TouristReviewController` (`/reviews/**`, ya estaba permitido para TURISTA en `SecurityConfig`, no se tocó) y `ProviderReviewReplyController` (`/provider/reviews`), DTOs `Review*`, vistas `templates/reviews/form.html`, `templates/provider/reviews.html` y el fragmento `templates/fragments/reviews.html`.
- **Archivos de otras tareas que se tocaron (poco):** `PublicActivityAdvice` (expone el servicio de reseñas al detalle), `actividades/detail.html` (una línea que incluye el fragmento), `fragments/header.html` (link "Reseñas" del prestador) y `reservas/list.html` (mensajes de éxito/error). El botón "Calificar" de Mis reservas ya existía.
- **Cómo se probó:** 41 tests nuevos (26 del servicio, 10 + 5 de los controllers; 548 en total, sin tocar la base real). Con la app real: reserva futura rechazada, puntaje inválido, publicar, segunda reseña rechazada, respuesta del prestador (y segunda rechazada), eliminar → desaparece del detalle y del promedio.

### 11/11/2026 — Top 3 de la API (`/api/activities/top`)
- **Antes:** la ruta existía pero devolvía siempre `[]` (había un TODO a la espera de las reseñas).
- **Ahora:** `GET /api/activities/top?department=ROCHA&month=2026-11`. `month` es opcional (formato `aaaa-MM`; si falta, el mes en curso). Devuelve hasta 3 actividades **publicadas** de prestadores **aprobados** del departamento, ordenadas por el promedio de las reseñas **visibles** publicadas en ese mes; si empatan, gana la que tiene más valoraciones (y, por último, el id, para que el orden sea siempre el mismo). Las reseñas eliminadas no cuentan; una actividad sin reseñas ese mes no aparece (la lista puede tener 0, 1, 2 o 3 elementos). Un `month` inválido (`noviembre`, `2026-13`, `11/2026`) devuelve 400 con el mismo formato de error que el resto de la API.
- **Decisión:** el "mes" de una reseña es el mes en que se **publicó** (`createdAt`), de 00:00 del día 1 inclusive a 00:00 del mes siguiente exclusive. El promedio se devuelve redondeado a 2 decimales.
- **Cómo funciona:** una sola consulta (`ReviewRepository.rankActivities`) agrupa por actividad, calcula promedio y cantidad, ordena y limita a 3 en la base de datos; `PublicApiService.findTopOfMonth(department, month)` la usa y arma los DTOs; `PublicApiController` recibe el parámetro.
- **Archivos de otras tareas que se tocaron:** `PublicApiService` y `PublicApiController` (de Carlota), `API.md` (documentación de la ruta ya actualizada) y los tests `PublicApiServiceTest` (el servicio ahora recibe también `ReviewRepository`) y `PublicApiIntegrationTest` (se renombró el test que decía "vacío hasta que existan reseñas").
- **Cómo se probó:** 7 tests nuevos: 3 unitarios (mes exacto y paso de diciembre a enero, máximo 3, mes actual por defecto) y 5 contra PostgreSQL con la seguridad activa y sin sesión (`TopActivitiesIntegrationTest`: orden y desempate, eliminadas, bordes del mes, solo publicadas/aprobadas/del departamento, mes inválido).

### 11/11/2026 — Idiomas: español e inglés
- **Qué hace:** hay un selector **ES | EN** en la barra de navegación (visible con o sin sesión). El idioma elegido se guarda en una **cookie** (`lang`, dura 1 año), así que se mantiene al navegar y al volver. También se puede cambiar a mano con `?lang=es` o `?lang=en` en cualquier URL. Cualquier otro valor (por ejemplo `?lang=fr`) se ignora.
- **Qué idioma se ve primero:**
  1. Si el navegador ya tiene la cookie, ese.
  2. Si alguien inicia sesión en un navegador sin cookie, el idioma guardado en su usuario.
  3. Un visitante sin cookie: inglés si su navegador lo pide (`Accept-Language`), y español en cualquier otro caso. El idioma de la computadora del servidor no cuenta nunca.
- **Idioma al registrarse (turista):** se calcula según el **país de residencia** (`TouristService.languageFor`): los países hispanohablantes → español y cualquier otro → **inglés**. Se guarda en el usuario y, al terminar el registro, queda elegido en ese navegador. Cambio respecto de antes: Brasil y Portugal daban `pt` (portugués); como solo soportamos español e inglés, ahora dan inglés. Está explicado en el texto debajo del campo "País de residencia".
- **Cómo funciona (Spring):** `MessageSource` con `messages_es.properties` y `messages_en.properties` (las plantillas piden los textos con `#{clave}`), `LocaleResolver` por cookie y un interceptor que lee `?lang=`. Fechas con el mismo formato en los dos idiomas (dd/MM/yyyy); los **precios y el promedio de valoraciones** usan el formato del idioma (`1.234,50` y `4,5` en español; `1,234.50` y `4.5` en inglés), también el total que se recalcula en el formulario de reserva.
- **Qué está traducido:** barra de navegación y pie, inicio, login, listado de actividades, detalle (datos útiles, fechas, valoraciones y pronóstico del clima con sus descripciones), registro de turista y su pantalla de éxito, y todo el flujo de reserva: formulario, "Mis reservas", detalle y pago de la reserva (incluidos los avisos de `checkout.js` y el idioma del formulario de Mercado Pago) y error de pago. Los tipos de documento y los estados de reserva y de pago también.
- **Qué NO está traducido (a propósito, por tamaño):** paneles del prestador y del admin, perfil, cambio de contraseña, registro de prestador, pantallas de reseñas, y los textos que arman los servicios o los DTO en Java (mensajes de error y de éxito, validaciones de formularios, políticas de cancelación, motivos de rechazo de pago y los correos): siguen en español.
- **Dónde está** (`src/main/java/uy/edu/um/nexo/mnatural/`): `config/I18nConfig` (mensajes, cookie e interceptor), `config/UserLanguageInterceptor` (idioma guardado del usuario), `controllers/LanguageAdvice` (links del selector), `controllers/TouristRegistrationController` (deja el idioma elegido al registrarse) y `service/TouristService`. Textos en `src/main/resources/messages_es.properties` y `messages_en.properties`; el selector está en `templates/fragments/header.html` y sus estilos al final de `static/css/styles.css`. Para sumar una pantalla: usar `#{clave}` y agregar la clave en los dos archivos.
- **Archivos de otras tareas que se tocaron:** `fragments/header.html` (layout común), `TouristService` y `TouristRegistrationController` (de Camila), las plantillas del portal público (`index`, `login`, `actividades/*`, `registro-turista*`) y de reservas y pagos (`reservas/*`, `payments/*`), `static/js/checkout.js` y `TouristServiceTest` (Brasil y Portugal ahora dan inglés). `SecurityConfig` y `application.properties` **no** se tocaron.
- **Seguridad:** los links del selector solo llevan los parámetros de la página en pedidos GET; en un POST (por ejemplo, un registro con errores que se vuelve a mostrar) nunca se copian, para no dejar una contraseña escrita en un link.
- **Cómo se probó:** 30 tests nuevos (586 en total, sin tocar la base real): que los dos archivos de textos tengan las mismas claves y parámetros y que toda clave usada en una plantilla exista (`MessagesTest`), cambio de idioma y cookie en el portal (`LanguageSwitchTest`), idioma guardado del usuario y links del selector (`UserLanguageInterceptorTest`) e idioma por país al registrarse (`TouristRegistrationLanguageTest`). Con la app real: cambio y persistencia, `?lang=fr` ignorado, turista de Brasil y de Alemania → inglés, de Uruguay → español, inicio de sesión en un navegador nuevo con el idioma guardado (y respetando el que ya había elegido), y reserva completa en inglés hasta la pantalla de pago.

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

### 11/11/2026 — Búsqueda por palabra clave en `/actividades`
- **Qué hace:** arriba de los filtros del listado hay un cuadro "Buscar por palabra clave". Se combina con los demás filtros (departamento, localidad, tipo, fecha y precio) y queda en la URL (`/actividades?q=cabalgata`), así que se puede compartir o recargar. Texto de ayuda en español e inglés.
- **Mismo criterio que la API:** busca en el **nombre, la descripción, la localidad y los nombres de los tipos** de la actividad, sin distinguir mayúsculas ni tildes (`piriapolis` encuentra `Piriápolis` y al revés), y `%` o `_` se buscan como texto. Solo actividades publicadas. El parámetro se llama `q`, igual que en `/api/activities/search`.
- **Cómo se garantiza que sean iguales:** la condición se sacó de `PublicApiService` a una clase compartida (`service/ActivityKeywordSearch`) que usan los dos. La API le agrega "prestador aprobado". Un test compara el resultado del listado con el de la API para varias palabras (con tildes, comodines, sin resultados).
- **Detalle:** un texto de más de 100 caracteres se recorta a 100 en el portal (la API sigue respondiendo 400 como antes). El departamento no se busca por nombre: para eso está el filtro.
- **Archivos de otras tareas que se tocaron:** `PublicApiService` (solo para usar la clase compartida; el comportamiento de la API no cambió y sus tests siguen igual), `ActivityCatalogService` y `ActivityFilterDTO` (de Carlota) y `actividades/list.html`.
- **Cómo se probó:** 15 tests nuevos (601 en total, sin tocar la base real): 10 contra PostgreSQL (`KeywordSearchIntegrationTest`: nombre, descripción, localidad, tipo, tildes, borradores, sin repetidos, comodines, combinación con otros filtros y listado = API) y 5 del controller (`PublicActivitySearchTest`: campo en los dos idiomas, el texto llega al servicio, se conserva en el cuadro escapado). Con la app real: mayúsculas, con y sin tildes, localidad, sin resultados y mismo resultado que la API.

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
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Servidor de correo (con Mailpit: `localhost` y `1025`, sin usuario ni contraseña) |
| `MAIL_FROM` | Remitente de los correos (si no, `MAIL_USERNAME` y si tampoco `no-reply@mnatural.uy`) |
| `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` | Si el servidor pide usuario y cifrado (por defecto `true`; con Mailpit `false`) |
| `PAYMENT_GATEWAY`, `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` | Pago con Mercado Pago (sandbox). Sin ellos se usa el pago simulado |
| `APP_BASE_URL` | Dirección pública para los links de los correos (por defecto `http://localhost:8080`) |
| `SHOW_SQL` | `true` para imprimir las consultas SQL (por defecto `false`) |
| `app.seed.test-providers` | `true` activa `TestProviderSeeder` (por defecto apagado) |
| `app.seed.demo-activities` | `true` carga las 15 actividades de demostración (por defecto apagado) |
| `weather.enabled` | `false` apaga el clima (por defecto activado) |

## Pendientes
- Sacar `AdminSeeder` (o cambiar sus datos) antes de entregar. `TestProviderSeeder` ya está apagado por defecto.
- Idiomas: falta traducir los paneles del prestador y del admin, el perfil, el registro de prestadores, las pantallas de reseñas y los mensajes que salen de los servicios (errores, avisos y correos siguen en español). La base ya está: para sumar una pantalla alcanza con usar `#{clave}` y agregar la clave en los dos archivos `messages_*.properties`.
- No existe desactivar o borrar admins. Falta el link de "Cambiar contraseña" en las pantallas del turista.
- Actividades: falta republicar una suspendida y borrar.
- Contraseña de la base de datos: aplicar el plan del `.env` con el equipo.
- Probar el formulario de Mercado Pago y el webhook con las credenciales de prueba reales del equipo (token y clave pública) y ngrok: hoy están cubiertos con tests, no con Mercado Pago real.
- Confirmar el origen y la licencia de las fotos de respaldo por departamento (`punta-del-este`, `montevideo`, `salto`, `rocha`).
- Correos: no se recorrió en vivo el de "reembolso completado" tras un reintento (está cubierto con tests).
