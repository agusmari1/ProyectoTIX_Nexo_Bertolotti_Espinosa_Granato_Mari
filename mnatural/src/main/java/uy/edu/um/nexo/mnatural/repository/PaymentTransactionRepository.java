package uy.edu.um.nexo.mnatural.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;

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
}
