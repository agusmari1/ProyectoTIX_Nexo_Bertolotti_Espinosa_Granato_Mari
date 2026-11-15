package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;

import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    boolean existsByEmail(String email);

    // Por orden de llegada. El desempate por id cubre a los prestadores que ya existían
    // cuando se agregó requestedAt (todos quedaron con la misma fecha).
    // El EntityGraph trae el emprendimiento junto con el prestador, para mostrarlo en la lista
    @EntityGraph(attributePaths = "businesses")
    List<Provider> findByApprovalStatusOrderByRequestedAtAscIdAsc(ApprovalStatus approvalStatus);

    // Los ya resueltos (aprobados o rechazados), el más reciente primero. Trae también el emprendimiento
    // y el admin que resolvió, para mostrarlos en la lista. Los que no tienen fecha (cargados de prueba)
    // van al final
    @EntityGraph(attributePaths = {"businesses", "reviewedBy"})
    @Query("select p from Provider p where p.approvalStatus = :status "
            + "order by p.reviewedAt desc nulls last, p.id desc")
    List<Provider> findResolvedByStatus(@Param("status") ApprovalStatus status);
}
