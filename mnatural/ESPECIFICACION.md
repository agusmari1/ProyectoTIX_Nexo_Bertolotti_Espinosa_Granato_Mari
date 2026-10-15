# **Especificación de Requerimientos**

## **Uruguay Mnatural**

Este documento reúne los requerimientos funcionales del sistema. Están agrupados por área y siguen el orden en que un usuario recorrería la plataforma: primero el registro de los distintos actores, luego la búsqueda y reserva de actividades, y finalmente los aspectos administrativos y las integraciones con servicios externos.

## 

## **1\. Gestión de Usuarios y Autenticación**

* El sistema deberá permitir el registro de un nuevo turista, solicitando como datos obligatorios: nombre, apellido, fecha de nacimiento, tipo y número de documento, país de residencia, correo electrónico y contraseña. Opcionalmente, el turista podrá indicar un número de teléfono de contacto.  
* Al registrarse, el idioma predeterminado de la interfaz del turista se asignará automáticamente en función del país indicado. Si el idioma correspondiente al país no está soportado por el sistema, se asignará el idioma por defecto (inglés). El turista podrá cambiarlo manualmente (ver sección 8).  
* El sistema deberá permitir el registro de un nuevo prestador turístico (empresa o persona particular), solicitando los datos de identificación, contacto y ubicación de su emprendimiento, además de sus credenciales de acceso (correo electrónico y contraseña). El resto de la información del emprendimiento podrá completarse y editarse una vez aprobado el prestador (ver sección 6).  
* Todo prestador recién registrado quedará en estado pendiente de aprobación y no podrá acceder a la plataforma hasta ser validado.  
* El sistema deberá permitir a los administradores visualizar el listado de prestadores pendientes de aprobación, ordenados por orden de llegada, y aceptarlos o rechazarlos.  
* Al aceptar o rechazar a un prestador, el sistema registrará qué administrador resolvió la solicitud y la fecha de resolución, y le notificará el resultado al prestador por correo electrónico. Un prestador rechazado no podrá acceder a la plataforma.  
* Una vez aprobado, el prestador podrá acceder a su espacio de gestión dentro de la plataforma.  
* El sistema deberá permitir el inicio de sesión de turistas, prestadores y administradores mediante sus credenciales correspondientes.

## **2\. Exploración de Actividades (Portal Turista)**

* Cualquier visitante, sin necesidad de registrarse, podrá consultar el listado de actividades turísticas publicadas.  
* El visitante podrá filtrar y buscar actividades por destino (departamento o localidad), tipo de actividad, fecha, precio y otras características relevantes.  
* El visitante podrá acceder al detalle de una actividad, donde se mostrará el emprendimiento responsable, ubicación, descripción, duración aproximada, precio por persona, requisitos (edad, equipamiento necesario o proporcionado) y las próximas fechas y horarios disponibles.  
* El sistema deberá permitir que una misma actividad tenga múltiples instancias (fechas y horarios), cada una con su propia disponibilidad de cupos.  
* El detalle de una actividad deberá mostrar los comentarios y la valoración promedio dejados por otros turistas.

## **3\. Gestión de Reservas**

* Para reservar una actividad, el turista deberá estar autenticado.  
* El turista deberá poder seleccionar una fecha y horario disponible, indicar la cantidad de participantes y, para cada uno, ingresar los datos correspondientes en caso de tratarse de otras personas (nombre, apellido, tipo y número de documento, fecha de nacimiento, etc.), pudiendo reservar en representación de terceros. El turista que realiza la reserva quedará registrado como titular de la misma y se contará como participante, salvo que indique que reserva únicamente en representación de terceros.  
* El sistema deberá validar que cada participante cumpla con la edad mínima requerida por la actividad, a partir de su fecha de nacimiento.  
* Opcionalmente, el turista podrá indicar una observación general sobre la reserva (por ejemplo, un comentario logístico), y además, para sí mismo y para cada participante, una observación particular relevante para la prestación del servicio (por ejemplo, uso de muletas o silla de ruedas). Todas las observaciones serán visibles para el prestador en el detalle de la reserva.   
* Al momento de seleccionar los cupos deseados, estos quedarán reservados de forma inmediata y dejarán de estar disponibles para otros usuarios, evitando conflictos de concurrencia. Esta retención durará hasta que el turista complete el pago o abandone el proceso, con un máximo de 10 minutos; vencido ese plazo sin que se haya intentado el pago, los cupos se liberarán.  
* El sistema deberá respetar el límite de minutos previos al inicio de la actividad establecido por el prestador como plazo máximo para realizar una reserva. Si el prestador indica cero minutos, el límite será la propia hora de inicio de la actividad.  
* El monto a pagar por la reserva corresponderá al precio por persona definido por el prestador, multiplicado por la cantidad total de participantes incluidos en la reserva, y se abonará como un único pago.  
* Los pagos se procesarán en pesos uruguayos.  
* Al momento de pagar, el turista podrá seleccionar un medio de pago y dejar guardada su tarjeta para uso futuro. El sistema no almacenará el número completo de la tarjeta ni el código de seguridad (CVV): conservará únicamente un identificador (token) provisto por el servicio externo de pagos, junto con los últimos cuatro dígitos, la marca y la fecha de vencimiento.  
* Si el pago se procesa exitosamente, la reserva pasará a estado confirmada. Si el pago no puede completarse en el momento, la reserva quedará en estado pendiente durante un plazo máximo de dos horas (o hasta el límite de reserva de la actividad, lo que ocurra primero), tiempo tras el cual, de no completarse el pago, la reserva será cancelada automáticamente y el cupo liberado. Mientras la reserva esté pendiente, los cupos permanecerán retenidos.  
* Al confirmarse una reserva, el sistema generará un código único de reserva, visible tanto para el turista como para el prestador, junto con un código QR asociado que se enviará al turista por correo electrónico. El código QR deberá presentarse al momento de realizar la actividad como método de verificación de la reserva. El prestador podrá verificar la validez de la reserva a partir del código único o del código QR presentado. Al confirmarse la reserva también se habilitarán los datos de contacto entre turista y prestador (ver sección 4).  
* El turista podrá consultar el historial de sus reservas y el estado de cada una.  
* El sistema deberá permitir al prestador configurar, para cada actividad, si admite cancelaciones, con cuánta anticipación pueden realizarse y qué porcentaje del monto se reembolsa en ese caso.  
* El turista podrá cancelar una reserva conforme a la política de cancelación definida por el prestador para esa actividad, generándose el reembolso correspondiente a través del servicio externo de pagos. El cupo de la reserva cancelada quedará liberado.  
* En caso de que un prestador suspenda una actividad (o una fecha y horario de la misma) ya reservada, el sistema deberá procesar automáticamente el reembolso del 100% del monto abonado por cada reserva afectada y notificar a cada turista por correo electrónico. Las reservas pendientes afectadas se cancelarán sin generar cobro. Si el servicio externo de pagos no estuviera disponible al momento de procesar un reembolso, el sistema deberá reintentarlo hasta que se complete.

## **4\. Datos de Contacto entre Turista y Prestador**

* El sistema no incluirá un chat interno ni almacenará mensajes entre turista y prestador.  
* Una vez que la reserva se encuentre confirmada (pago acreditado), el sistema mostrará al turista los datos de contacto del prestador (nombre del emprendimiento, teléfono y correo electrónico) y al prestador los datos de contacto del turista (nombre, correo electrónico y teléfono, si lo indicó), para que se comuniquen por sus propios medios.  
* Mientras la reserva esté pendiente, cancelada o vencida, los datos de contacto no serán visibles para la otra parte.  
* Al reservar, el sistema informará al turista que sus datos de contacto serán compartidos con el prestador una vez confirmada la reserva.

## **5\. Comentarios y Valoraciones**

* La opción de dejar un comentario y una valoración (de uno a cinco puntos) sobre una actividad se habilitará al turista únicamente si su reserva se encuentra confirmada y una vez finalizada la actividad (fecha y hora de inicio de la instancia reservada más la duración de la actividad). Se podrá dejar una única reseña (comentario y valoración) por reserva, a cargo del turista que la realizó.  
* El turista podrá eliminar sus propios comentarios. Al eliminar un comentario también se eliminará su valoración, que dejará de contar para el promedio de la actividad.  
* El prestador podrá responder públicamente a los comentarios recibidos (funcionalidad no prioritaria), pero no podrá eliminarlos.  
* Los comentarios y valoraciones publicados formarán parte de la información pública de la actividad, visible para cualquier visitante.

## **6\. Gestión de Prestadores y Actividades**

* El prestador, una vez aprobado, deberá poder registrar y editar la información de su emprendimiento (identificación, contacto, ubicación, descripción general de los servicios), complementando los datos cargados al registrarse (ver sección 1).  
* El prestador deberá poder dar de alta nuevas actividades sin necesidad de aprobación del administrador, indicando descripción, tipo(s) de actividad, ubicación (departamento y localidad), duración, precio por persona, requisitos (incluida la edad mínima) y equipamiento asociado (campo de texto libre).  
* El prestador deberá poder definir múltiples fechas y horarios para una misma actividad, estableciendo la cantidad de cupos disponibles para cada instancia.  
* El prestador deberá poder consultar las reservas realizadas sobre sus actividades y la información necesaria para organizar la prestación del servicio, incluyendo participantes, observaciones, código de reserva, estado de la reserva y, en las reservas confirmadas, los datos de contacto del turista.  
* El prestador deberá poder configurar, por actividad, el límite de minutos previos al inicio para realizar una reserva y la política de cancelación y reembolso correspondiente, según lo descrito en la sección 3\.  
* El prestador deberá poder suspender una actividad o una fecha y horario de la misma, lo que generará los reembolsos y notificaciones descritos en la sección 3\.

## **7\. Administración de la Plataforma**

* El administrador deberá poder gestionar los tipos de actividad turística disponibles en el sistema (turismo de naturaleza, trekking, aventura, gastronomía, actividades acuáticas, recorridos históricos, enoturismo, cabalgatas, senderismo u otras que la organización considere pertinentes), mediante las operaciones de alta, consulta, modificación y eliminación.  
* El administrador deberá poder gestionar las solicitudes de registro de prestadores pendientes de aprobación.

## **8\. Internacionalización**

* El sistema deberá soportar múltiples idiomas (requerimiento muy deseable para el cliente), traduciendo al menos las etiquetas y elementos fijos de la interfaz según el idioma predeterminado del usuario.  
* El idioma predeterminado de cada usuario se asignará en base al país indicado al momento de registrarse, pudiendo cambiarse manualmente. Si el idioma correspondiente al país no está soportado, se usará el idioma por defecto del sistema (ver sección 1).

## **9\. Integraciones y API**

* El sistema deberá integrarse con un servicio externo de pagos (bridge financiero) mediante API, tanto para el cobro de reservas como para el procesamiento de reembolsos.  
* Para confirmar una reserva el sistema deberá asegurarse que el pago haya sido efectivamente procesado. Deberá manejar adecuadamente los casos en que el servicio externo de pagos no esté disponible o devuelva un resultado distinto al esperado, para evitar errores en la confirmación de las reservas. En esos casos la reserva permanecerá pendiente conforme a lo indicado en la sección 3\.  
* El sistema podrá integrarse opcionalmente con una API pública de clima para mostrar el pronóstico correspondiente a la fecha y lugar de una actividad.  
* El sistema deberá exponer una API pública propia que permita a terceros consultar: el listado de emprendimientos, las actividades disponibles en un día para un departamento o localidad determinado, el top 3 de actividades mejor puntuadas del mes por departamento, la verificación del estado de un emprendimiento y la búsqueda de actividades por palabra clave.  
* Para el top 3 se considerará el mes calendario en curso y la valoración promedio de las reseñas publicadas en ese mes; en caso de empate, se desempatará por mayor cantidad de valoraciones. La verificación del estado de un emprendimiento indicará si el emprendimiento existe y si su prestador se encuentra aprobado.