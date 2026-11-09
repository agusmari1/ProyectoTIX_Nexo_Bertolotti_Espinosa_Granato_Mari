package uy.edu.um.nexo.mnatural.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    // Trae la reserva y bloquea su fila (SELECT ... FOR UPDATE) hasta que termine la transacción.
    // Así BookingExpirationTask no puede vencerla mientras se registra un pago y se extiende heldUntil:
    // si corre al mismo tiempo, espera y después ve el heldUntil nuevo.
    // Está en este repositorio para no modificar BookingRepository
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findBookingForUpdate(@Param("bookingId") Long bookingId);

    // Lo mismo sin bloquear, para pantallas que solo leen
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findBookingById(@Param("bookingId") Long bookingId);

    List<PaymentTransaction> findByBookingIdAndTypeAndStatus(Long bookingId, PaymentType type, PaymentStatus status);

    boolean existsByBookingIdAndTypeAndStatus(Long bookingId, PaymentType type, PaymentStatus status);

    long countByBookingIdAndType(Long bookingId, PaymentType type);

    List<PaymentTransaction> findByBookingIdAndTypeOrderByAttemptNumberAsc(Long bookingId, PaymentType type);

    Optional<PaymentTransaction> findByExternalReference(String externalReference);

    // ===== Reembolsos =====

    // El cobro aprobado de la reserva (hay a lo sumo uno: startPayment no abre otro si ya hay uno aprobado)
    Optional<PaymentTransaction> findFirstByBookingIdAndTypeAndStatus(Long bookingId, PaymentType type, PaymentStatus status);

    boolean existsByBookingIdAndTypeAndStatusIn(Long bookingId, PaymentType type, Collection<PaymentStatus> statuses);

    // El último intento de reembolso, para reintentarlo por el mismo monto
    Optional<PaymentTransaction> findFirstByBookingIdAndTypeOrderByAttemptNumberDesc(Long bookingId, PaymentType type);

    // Reservas de un horario en esos estados, bloqueadas para que no se confirmen ni venzan mientras se cancelan
    // por una suspensión. Está en este repositorio para no modificar BookingRepository
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.activitySchedule.id = :scheduleId and b.status in :statuses")
    List<Booking> findBookingsOfScheduleForUpdate(@Param("scheduleId") Long scheduleId,
                                                  @Param("statuses") Collection<BookingStatus> statuses);

    // Reservas canceladas o vencidas cuyo último reembolso falló y que todavía no tienen uno hecho (aprobado o en proceso):
    // las reintenta RefundRetryTask. Una reserva que sigue CONFIRMED (el turista intentó cancelar y falló)
    // no entra: esa cancelación no se hizo y el turista la puede volver a pedir
    @Query("""
            select distinct t.booking.id from PaymentTransaction t
            where t.type = uy.edu.um.nexo.mnatural.entities.PaymentType.REFUND
              and t.status in (uy.edu.um.nexo.mnatural.entities.PaymentStatus.ERROR,
                               uy.edu.um.nexo.mnatural.entities.PaymentStatus.REJECTED)
              and t.booking.status in (uy.edu.um.nexo.mnatural.entities.BookingStatus.CANCELLED,
                                       uy.edu.um.nexo.mnatural.entities.BookingStatus.EXPIRED)
              and not exists (select r from PaymentTransaction r
                              where r.booking = t.booking
                                and r.type = uy.edu.um.nexo.mnatural.entities.PaymentType.REFUND
                                and r.status in (uy.edu.um.nexo.mnatural.entities.PaymentStatus.APPROVED,
                                                 uy.edu.um.nexo.mnatural.entities.PaymentStatus.PENDING))
            """)
    List<Long> findBookingIdsWithFailedRefunds();

    // Reservas vencidas con un cobro aprobado y sin ningún reembolso (ni siquiera fallido: esos los reintenta
    // findBookingIdsWithFailedRefunds). Se cobró una reserva que nunca se confirmó, así que hay que devolver el dinero
    @Query("""
            select distinct c.booking.id from PaymentTransaction c
            where c.type = uy.edu.um.nexo.mnatural.entities.PaymentType.CHARGE
              and c.status = uy.edu.um.nexo.mnatural.entities.PaymentStatus.APPROVED
              and c.booking.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.EXPIRED
              and not exists (select r from PaymentTransaction r
                              where r.booking = c.booking
                                and r.type = uy.edu.um.nexo.mnatural.entities.PaymentType.REFUND)
            """)
    List<Long> findExpiredPaidBookingIdsWithoutRefund();
}
