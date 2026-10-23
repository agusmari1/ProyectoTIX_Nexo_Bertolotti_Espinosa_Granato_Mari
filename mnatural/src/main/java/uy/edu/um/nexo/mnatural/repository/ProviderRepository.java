package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
