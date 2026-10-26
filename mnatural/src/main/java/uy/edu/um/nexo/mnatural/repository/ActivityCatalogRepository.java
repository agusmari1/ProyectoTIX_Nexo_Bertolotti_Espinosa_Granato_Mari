package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Consultas de solo lectura para el portal público (/actividades).
// Es un repositorio aparte para no modificar ActivityRepository ni ActivityScheduleRepository
public interface ActivityCatalogRepository extends Repository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    // Búsqueda con filtros combinables (ver ActivityCatalogService). El EntityGraph trae
    // el emprendimiento y los tipos junto con cada actividad, para mostrarlos en las tarjetas
    @Override
    @EntityGraph(attributePaths = {"business", "activityTypes"})
    List<Activity> findAll(Specification<Activity> spec, Sort sort);

    @EntityGraph(attributePaths = {"business", "activityTypes"})
    Optional<Activity> findByIdAndStatus(Long id, ActivityStatus status);

    // Horarios que todavía no empezaron y no están suspendidos, del más próximo al más lejano
    @Query("""
            select s from ActivitySchedule s
            where s.activity.id = :activityId
              and s.status = uy.edu.um.nexo.mnatural.entities.ScheduleStatus.SCHEDULED
              and s.startDateTime > :now
            order by s.startDateTime
            """)
    List<ActivitySchedule> findUpcomingSchedules(@Param("activityId") Long activityId, @Param("now") LocalDateTime now);
}
