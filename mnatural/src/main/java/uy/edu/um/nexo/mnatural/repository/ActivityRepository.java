package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    // Actividades de todos los emprendimientos de un prestador, las más nuevas primero.
    // El EntityGraph trae el emprendimiento y los tipos junto con la actividad, para mostrarlos en la lista
    @EntityGraph(attributePaths = {"business", "activityTypes"})
    List<Activity> findByBusinessProviderIdOrderByCreatedAtDesc(Long providerId);

    // Busca la actividad solo si es de un emprendimiento del prestador; si es de otro, no la encuentra
    Optional<Activity> findByIdAndBusinessProviderId(Long id, Long providerId);

    // Horarios de una actividad, del más próximo al más lejano.
    // TODO: moverlo a ActivityScheduleRepository como findByActivityIdOrderByStartDateTimeAsc
    @Query("select s from ActivitySchedule s where s.activity.id = :activityId order by s.startDateTime")
    List<ActivitySchedule> findSchedulesOf(@Param("activityId") Long activityId);
}
