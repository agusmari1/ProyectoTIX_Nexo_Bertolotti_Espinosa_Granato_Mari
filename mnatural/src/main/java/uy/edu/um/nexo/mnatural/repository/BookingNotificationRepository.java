package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Booking;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Consultas de reservas para armar correos (solo lectura). Es un repositorio aparte para no modificar
// BookingRepository, que es de Agustina. Trae todo lo que el correo necesita con un solo pedido ("join fetch"),
// así no hay que leer datos "lazy" con la transacción ya cerrada (los correos salen después del commit)
public interface BookingNotificationRepository extends JpaRepository<Booking, Long> {

    // La reserva con su prestador (actividad -> emprendimiento -> prestador), para avisarle de una reserva nueva
    @Query("""
            select b from Booking b
            join fetch b.activitySchedule s
            join fetch s.activity a
            join fetch a.business bu
            join fetch bu.provider
            where b.id = :bookingId
            """)
    Optional<Booking> findWithProviderById(@Param("bookingId") Long bookingId);

    // Reservas pendientes cuya retención vence antes de "limit". Es un adelanto de lo que va a pasar a EXPIRED
    @Query("""
            select b.id from Booking b
            where b.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.PENDING
              and b.heldUntil <= :limit
            """)
    List<Long> findIdsHeldUntilBefore(@Param("limit") LocalDateTime limit);

    // De esas reservas, las que quedaron realmente vencidas (con el turista y la actividad, para el correo)
    @Query("""
            select b from Booking b
            join fetch b.tourist
            join fetch b.activitySchedule s
            join fetch s.activity
            where b.id in :ids
              and b.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.EXPIRED
            """)
    List<Booking> findExpiredByIds(@Param("ids") Collection<Long> ids);
}
