package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Business;

import java.util.List;

// Consultas de solo lectura para la API pública (ver PublicApiService)
public interface PublicApiRepository extends Repository<ActivitySchedule, Long>, JpaSpecificationExecutor<ActivitySchedule> {

    // El EntityGraph trae la actividad, su emprendimiento y sus tipos junto con cada horario
    @Override
    @EntityGraph(attributePaths = {"activity", "activity.business", "activity.activityTypes"})
    List<ActivitySchedule> findAll(Specification<ActivitySchedule> spec, Sort sort);

    @Query("""
            select b from Business b
            where b.provider.approvalStatus = uy.edu.um.nexo.mnatural.entities.ApprovalStatus.APPROVED
            order by b.name
            """)
    List<Business> findBusinessesOfApprovedProviders();
}
