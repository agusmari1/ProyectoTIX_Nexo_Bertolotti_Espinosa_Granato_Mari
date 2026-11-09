package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.Review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Las reseñas eliminadas (deletedAt) quedan en la tabla pero ninguna consulta pública las devuelve
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBookingId(Long bookingId);

    // La reserva con su horario y su actividad, solo para leerla (BookingRepository no se modifica)
    @Query("""
            select b from Booking b
            join fetch b.activitySchedule s
            join fetch s.activity
            join fetch b.tourist
            where b.id = :bookingId
            """)
    Optional<Booking> findBookingById(@Param("bookingId") Long bookingId);

    // Reseñas vigentes de una actividad, de la más nueva a la más vieja
    @Query("""
            select r from Review r join fetch r.tourist
            where r.activity.id = :activityId and r.deletedAt is null
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findVisibleByActivityId(@Param("activityId") Long activityId);

    // Promedio y cantidad de cada actividad que tiene al menos una reseña vigente
    @Query("""
            select new uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO(r.activity.id, avg(r.rating), count(r))
            from Review r
            where r.deletedAt is null
            group by r.activity.id
            """)
    List<ReviewSummaryDTO> summarizeAllActivities();

    // Mejores actividades publicadas (de prestadores aprobados) de un departamento, con las reseñas
    // creadas entre from (incluido) y to (excluido). Desempata la cantidad de reseñas y después el id,
    // para que el orden sea siempre el mismo
    @Query("""
            select new uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO(r.activity.id, avg(r.rating), count(r))
            from Review r
            where r.deletedAt is null
              and r.activity.department = :department
              and r.activity.status = uy.edu.um.nexo.mnatural.entities.ActivityStatus.PUBLISHED
              and r.activity.business.provider.approvalStatus = uy.edu.um.nexo.mnatural.entities.ApprovalStatus.APPROVED
              and r.createdAt >= :from and r.createdAt < :to
            group by r.activity.id
            order by avg(r.rating) desc, count(r) desc, r.activity.id asc
            """)
    List<ReviewSummaryDTO> findTopRated(@Param("department") Department department,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        Pageable pageable);
}
