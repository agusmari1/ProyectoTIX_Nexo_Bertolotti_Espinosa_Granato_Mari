package uy.edu.um.nexo.mnatural.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Trae el horario y bloquea su fila (SELECT ... FOR UPDATE) hasta que termine la transacción.
    // Otro turista que quiera reservar la misma fecha espera acá hasta que la primera reserva
    // se guarde, y recién después cuenta los lugares. Está en este repositorio para no
    // modificar ActivityScheduleRepository
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ActivitySchedule s where s.id = :scheduleId")
    Optional<ActivitySchedule> findScheduleForUpdate(@Param("scheduleId") Long scheduleId);

    // Lugares ocupados: participantes de reservas confirmadas y de pendientes cuya retención sigue vigente
    @Query("""
            select coalesce(sum(b.participantCount), 0) from Booking b
            where b.activitySchedule.id = :scheduleId
              and (b.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.CONFIRMED
                   or (b.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.PENDING and b.heldUntil > :now))
            """)
    long countTakenSpots(@Param("scheduleId") Long scheduleId, @Param("now") LocalDateTime now);

    // Pasa a EXPIRED las reservas pendientes cuya retención ya venció. Devuelve cuántas cambió
    @Modifying
    @Query("""
            update Booking b set b.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.EXPIRED
            where b.status = uy.edu.um.nexo.mnatural.entities.BookingStatus.PENDING
              and b.heldUntil <= :now
            """)
    int expireHeldBookings(@Param("now") LocalDateTime now);

    boolean existsByBookingCode(String bookingCode);
}
