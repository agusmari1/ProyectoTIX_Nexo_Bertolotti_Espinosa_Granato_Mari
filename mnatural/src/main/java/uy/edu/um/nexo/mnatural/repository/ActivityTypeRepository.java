package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.ActivityType;

import java.util.List;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Long> {

    // Cuántas actividades usan un tipo. Sirve para no borrar un tipo que está en uso
    @Query("select count(a) from Activity a join a.activityTypes t where t.id = :typeId")
    long countActivitiesUsing(@Param("typeId") Long typeId);

    // Para cada tipo en uso: [id del tipo, cantidad de actividades]. Los tipos sin
    // actividades no aparecen en el resultado
    @Query("select t.id, count(a) from Activity a join a.activityTypes t group by t.id")
    List<Object[]> countActivitiesByType();
}
