package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.Provider;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    boolean existsByEmail(String email);
}
