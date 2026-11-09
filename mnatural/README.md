# Uruguay Mnatural

Proyecto TIC del equipo Nexo: Spring Boot + Thymeleaf + PostgreSQL (con Docker).

## Pagos

### Cómo funciona

`PaymentService` nunca habla directo con un servicio de pagos: usa la interfaz `PaymentGateway`, que tiene dos implementaciones.

| Implementación | Cuándo se usa | Qué hace |
|---|---|---|
| `SimulatedPaymentGateway` | Por defecto (sin `PAYMENT_GATEWAY`) | No cobra nada. El resultado se elige con `payment.simulated.result`: `APPROVED` (por defecto), `PENDING`, `REJECTED` o `ERROR` (simula que el servicio no responde). |
| `MercadoPagoPaymentGateway` | `PAYMENT_GATEWAY=mercadopago` | Mercado Pago Checkout Pro en modo prueba (sandbox). Crea una preferencia de pago, manda al turista a pagar y, cuando vuelve, consulta el resultado real. |

El pago se hace en dos pasos: primero se registra el intento como `PENDING` y se manda al turista a la página de pago. Cuando vuelve a `/payments/return`, se consulta el estado al servicio. Nunca se le cree a lo que dice la URL. Todos los montos son en pesos uruguayos (UYU).

### Variables de entorno

| Variable | Para qué |
|---|---|
| `PAYMENT_GATEWAY=mercadopago` | Activa Mercado Pago. Si no está, se usa el simulado. |
| `MP_ACCESS_TOKEN` | Access Token **de prueba** de Mercado Pago. Va solo como variable de entorno, **nunca** en el código, en `application.properties` ni en el repo. La app no lo escribe en el log. |
| `APP_BASE_URL` | Dirección con la que se entra a la app. Con ella se arman las URLs a las que Mercado Pago devuelve al turista. |
| `PAYMENT_SIMULATED_RESULT` | (Opcional, solo con el simulado) Resultado forzado: `APPROVED`, `PENDING`, `REJECTED` o `ERROR`. |

Ejemplo en PowerShell, en la misma terminal desde donde se levanta la app:

```powershell
$env:PAYMENT_GATEWAY = "mercadopago"
$env:MP_ACCESS_TOKEN = "<tu token de prueba>"
$env:APP_BASE_URL    = "http://mnatural.localtest.me:8080"
.\mvnw spring-boot:run
```

### Mercado Pago no acepta localhost

Mercado Pago no acepta `localhost` ni una IP local (`127.0.0.1`, `192.168.x.x`) como URL de retorno. Funcionó con:

```
APP_BASE_URL=http://mnatural.localtest.me:8080
```

`localtest.me` (y cualquier subdominio) apunta siempre a `127.0.0.1`, así que es la misma compu pero con un nombre que Mercado Pago acepta. **Hay que entrar a la app siempre por esa dirección** (`http://mnatural.localtest.me:8080`), no por `localhost`. Si no, la sesión queda en otro dominio y al volver de pagar no se reconoce al turista.

Como esa dirección no es `https`, Mercado Pago no permite la vuelta automática (`auto_return`). Al terminar de pagar, el turista vuelve con el botón "Volver al sitio". Si no vuelve, al entrar a la pantalla de la reserva se consulta igual el estado del pago.

### Cómo probar

1. En el panel de desarrolladores de Mercado Pago, creá una **cuenta compradora de prueba** e iniciá sesión con ella en la página de pago (no uses tu cuenta real).
2. Pagá con la tarjeta de prueba:
   - Visa `4509 9535 6623 3704`
   - CVV `123`
   - Vencimiento `11/30`
   - Documento (CI) `12345678`
3. El **nombre del titular** elige el resultado:

| Nombre del titular | Resultado |
|---|---|
| `APRO` | Aprobado |
| `OTHE` | Rechazado |
| `CONT` | Pendiente |
| `FUND` | Rechazado por fondos insuficientes |

### Avisos de Mercado Pago (webhook)

Además de consultar el resultado cuando el turista vuelve, Mercado Pago puede avisar solo con `POST /payments/webhook`
(el `notification_url` que se manda al crear el pago). Así la reserva se confirma aunque el turista cierre la pestaña
antes de volver.

- El aviso solo trae el **id** del pago y **no se le cree**: con ese id se consulta el pago a Mercado Pago, de ahí sale
  la referencia (`MN-...`) y el estado y el monto reales se vuelven a consultar antes de confirmar nada.
- Es **idempotente**: Mercado Pago puede mandar el mismo aviso varias veces; un pago ya aprobado no se vuelve a tocar y
  una reserva confirmada no se confirma dos veces.
- Se contesta `200` si el aviso se entendió (aunque no sea de esta aplicación) y `500` si Mercado Pago no respondió o hubo un
  error nuestro: así Mercado Pago lo reenvía. Si Mercado Pago no responde, nunca se confirma nada.
- Solo se manda `notification_url` si `APP_BASE_URL` es `https`: Mercado Pago tiene que poder llegar a esa dirección
  desde internet. Con `http://mnatural.localtest.me:8080` no hay avisos y se sigue usando la consulta al volver.
- `/payments/webhook` es la única ruta de pagos que entra sin sesión ni token CSRF (regla en `SecurityConfig`).

Para probarlo en la compu con **ngrok**:

1. Instalá ngrok y corré `ngrok http 8080`. Copiá la dirección `https://....ngrok-free.app` que muestra (cambia cada vez).
2. Arrancá la app con esa dirección (y entrá a la app siempre por ella):

```powershell
$env:PAYMENT_GATEWAY = "mercadopago"
$env:MP_ACCESS_TOKEN = "<tu token de prueba>"
$env:APP_BASE_URL    = "https://<tu-direccion>.ngrok-free.app"
.\mvnw spring-boot:run
```

3. Pagá con una tarjeta de prueba. En la consola de ngrok (`http://127.0.0.1:4040`) se ve el `POST /payments/webhook`
   que manda Mercado Pago, y en el log de la app, `Aviso de pago ... procesado`.

Con una dirección `https` también se activa la vuelta automática (`auto_return`).

### Pagos de reservas que ya vencieron

Si el turista paga cuando la retención de la reserva ya terminó (por ejemplo, tardó en la página de pago), la reserva no
se puede confirmar porque el cupo pudo quedar para otro. En ese caso **el dinero se devuelve solo**: se reembolsa el 100 %
apenas se detecta (al volver el turista, al abrir la reserva o por aviso de Mercado Pago), y `RefundRetryTask` revisa cada
10 minutos las reservas vencidas con un pago aprobado y sin reembolso, y reintenta los reembolsos que fallaron.
Una reserva cancelada por el turista con una política sin reembolso **no** se devuelve: eso es lo que define la actividad.

Si el turista reintenta con otra tarjeta dentro de la misma página de pago después de un rechazo, el intento `REJECTED` se
vuelve a consultar y, si esa vez se aprobó, se toma como pagado.

### Estados

Cómo se traduce el estado que informa Mercado Pago:

| Mercado Pago | En la app |
|---|---|
| `approved` | `APPROVED`: la reserva se confirma, se genera el QR y se manda el mail |
| `pending`, `in_process`, `authorized` | `PENDING` |
| `rejected`, `cancelled` | `REJECTED` |
| cualquier otro | `ERROR`: no se confirma nada |

- Si el servicio de pagos falla o no responde, **nunca se confirma la reserva**. El intento queda registrado y el turista puede reintentar.
- Mientras tanto, la reserva queda retenida hasta **2 horas** desde que se creó (o hasta el límite de reserva de la actividad, lo que llegue primero). Después vence y se liberan los cupos.
- Un pago aprobado por otro monto o en otra moneda tampoco confirma la reserva (queda `ERROR`).

### Mail de confirmación

| Variable | Para qué |
|---|---|
| `MAIL_ENABLED=true` | Envía los mails por SMTP. Por defecto (`false`) no se envía nada: el mensaje solo se escribe en el log. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Datos del servidor SMTP. Solo como variables de entorno, nunca en el repo. |

### Cancelación y reembolsos

Cada reembolso es un `PaymentTransaction` de tipo `REFUND` enlazado al cobro original (`originalPayment`). Una reserva nunca se reembolsa dos veces.

- **El turista cancela** (botón "Cancelar reserva" en `/payments/bookings/{id}`, solo para reservas confirmadas). Se aplica la política que definió el prestador en la actividad:
  - si no admite cancelación, o ya pasó el plazo (`cancellationCutoffHours` antes del inicio), no se cancela y se explica el motivo;
  - si está dentro del plazo, se devuelve `refundPercentage` % de lo pagado (con 0 % se cancela sin reembolso);
  - si el servicio de pagos falla, el reembolso queda `ERROR` y **la reserva sigue confirmada**: el turista puede volver a intentar.
- **El prestador suspende un horario** (botón "Suspender" en `/provider/activities/{id}/schedules`). Se cancelan todas sus reservas, se reembolsa el **100 %** de las pagadas y las pendientes se cancelan sin cobro. Si un reembolso falla, la reserva se cancela igual y `RefundRetryTask` lo reintenta cada 10 minutos hasta que se complete.
- Con Mercado Pago se usa `POST /v1/payments/{payment_id}/refunds`: sin monto para el total y con `amount` si es parcial. Antes de pedirlo se revisa cuánto ya está reembolsado, así un reintento no devuelve dos veces.
- Con el servicio simulado, el reembolso se aprueba siempre, salvo con `payment.simulated.result=ERROR`.
