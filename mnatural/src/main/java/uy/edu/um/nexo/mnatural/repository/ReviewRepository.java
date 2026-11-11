package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // La reserva con su turista, su horario y su actividad, para decidir si se puede reseñar. Está acá y no en
    // BookingRepository (de Agustina) para no modificarlo
    @Query("""
            select b from Booking b
            join fetch b.tourist
            join fetch b.activitySchedule s
            join fetch s.activity
            where b.id = :bookingId
            """)
    Optional<uy.edu.um.nexo.mnatural.entities.Booking> findBookingForReview(@Param("bookingId") Long bookingId);

    // Una reserva tiene una sola reseña, esté visible o eliminada
    Optional<Review> findByBookingId(Long bookingId);

    // Las reseñas VISIBLES de una actividad, de la más nueva a la más vieja, con quien las escribió
    @Query("""
            select r from Review r
            join fetch r.tourist
            where r.activity.id = :activityId
              and r.status = uy.edu.um.nexo.mnatural.entities.ReviewStatus.VISIBLE
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findVisibleByActivityId(@Param("activityId") Long activityId);

    // Promedio y cantidad de las reseñas VISIBLES: [promedio (null si no hay), cantidad]. Las eliminadas no cuentan
    @Query("""
            select avg(r.rating), count(r) from Review r
            where r.activity.id = :activityId
              and r.status = uy.edu.um.nexo.mnatural.entities.ReviewStatus.VISIBLE
            """)
    List<Object[]> averageAndCountOf(@Param("activityId") Long activityId);

    // Ranking de un período para la API: [actividad, promedio, cantidad] solo con reseñas VISIBLES publicadas en
    // [from, to), de actividades publicadas de prestadores aprobados del departamento. Mejor promedio primero y,
    // si empatan, la que tiene más valoraciones (y por último el id, para que el orden sea siempre el mismo)
    @Query("""
            select a, avg(r.rating), count(r) from Review r
            join r.activity a
            where a.department = :department
              and a.status = uy.edu.um.nexo.mnatural.entities.ActivityStatus.PUBLISHED
              and a.business.provider.approvalStatus = uy.edu.um.nexo.mnatural.entities.ApprovalStatus.APPROVED
              and r.status = uy.edu.um.nexo.mnatural.entities.ReviewStatus.VISIBLE
              and r.createdAt >= :from and r.createdAt < :to
            group by a
            order by avg(r.rating) desc, count(r) desc, a.id asc
            """)
    List<Object[]> rankActivities(@Param("department") uy.edu.um.nexo.mnatural.entities.Department department,
                                  @Param("from") java.time.LocalDateTime from,
                                  @Param("to") java.time.LocalDateTime to,
                                  org.springframework.data.domain.Pageable pageable);

    // Las reseñas VISIBLES de las actividades de un prestador, para que las pueda responder
    @Query("""
            select r from Review r
            join fetch r.tourist
            join fetch r.activity a
            where a.business.provider.id = :providerId
              and r.status = uy.edu.um.nexo.mnatural.entities.ReviewStatus.VISIBLE
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findVisibleByProviderId(@Param("providerId") Long providerId);

    // Una reseña con la actividad y su prestador, para verificar que la responde su dueño
    @Query("""
            select r from Review r
            join fetch r.activity a
            join fetch a.business b
            join fetch b.provider
            where r.id = :id
            """)
    Optional<Review> findWithProviderById(@Param("id") Long id);
}
