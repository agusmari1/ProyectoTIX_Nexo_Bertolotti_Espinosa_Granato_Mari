package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ActivityScheduleRepository extends JpaRepository<ActivitySchedule, Long> {

    // Horarios programados que ya empezaron, con su actividad (para saber la duración).
    // Si ya terminaron lo decide ScheduleFinishTask, porque sumarle minutos a una fecha en JPQL depende de la base
    @Query("""
            select s from ActivitySchedule s join fetch s.activity
            where s.status = uy.edu.um.nexo.mnatural.entities.ScheduleStatus.SCHEDULED
              and s.startDateTime < :now
            """)
    List<ActivitySchedule> findStartedScheduled(@Param("now") LocalDateTime now);

    // Pasa a FINISHED solo los que siguen SCHEDULED: si el prestador suspendió uno mientras tanto,
    // no se pisa. Tampoco toca ninguna otra columna. Devuelve cuántos cambió
    @Modifying
    @Query("""
            update ActivitySchedule s set s.status = uy.edu.um.nexo.mnatural.entities.ScheduleStatus.FINISHED
            where s.id in :ids
              and s.status = uy.edu.um.nexo.mnatural.entities.ScheduleStatus.SCHEDULED
            """)
    int markFinished(@Param("ids") Collection<Long> ids);
}
