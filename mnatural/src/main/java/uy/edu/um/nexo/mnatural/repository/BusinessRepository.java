package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.Business;

import java.util.List;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    List<Business> findByProviderId(Long providerId);

    // Con el prestador en la consulta, un emprendimiento ajeno simplemente "no existe"
    Optional<Business> findByIdAndProviderId(Long id, Long providerId);
}
