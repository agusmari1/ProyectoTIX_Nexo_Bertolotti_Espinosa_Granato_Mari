package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Booking;

import java.util.List;
import java.util.Optional;

// Consultas de reservas para el espacio del prestador (solo lectura). Es un repositorio aparte para no
// modificar BookingRepository, que es de Agustina. Todas las consultas filtran por el prestador dueño de
// la actividad: un prestador nunca recibe reservas de otro
public interface ProviderBookingRepository extends JpaRepository<Booking, Long> {

    String SELECT_BOOKINGS = """
            select b from Booking b
            join fetch b.tourist
            join fetch b.activitySchedule s
            join fetch s.activity a
            where a.business.provider.id = :providerId
            """;

    String ORDER = " order by s.startDateTime desc, b.id desc";

    @Query(SELECT_BOOKINGS + ORDER)
    List<Booking> findAllOfProvider(@Param("providerId") Long providerId);

    @Query(SELECT_BOOKINGS + " and a.id = :activityId" + ORDER)
    List<Booking> findByActivityOfProvider(@Param("providerId") Long providerId,
                                           @Param("activityId") Long activityId);

    @Query(SELECT_BOOKINGS + " and s.id = :scheduleId" + ORDER)
    List<Booking> findByScheduleOfProvider(@Param("providerId") Long providerId,
                                           @Param("scheduleId") Long scheduleId);

    // Para verificar un código de reserva (Tarea 4): solo si la reserva es de una actividad de ese prestador
    @Query(SELECT_BOOKINGS + " and b.bookingCode = :code")
    Optional<Booking> findByCodeOfProvider(@Param("providerId") Long providerId, @Param("code") String code);
}
