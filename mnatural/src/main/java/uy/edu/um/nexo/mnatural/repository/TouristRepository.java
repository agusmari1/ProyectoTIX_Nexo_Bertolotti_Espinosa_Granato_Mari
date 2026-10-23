package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.Tourist;

public interface TouristRepository extends JpaRepository<Tourist, Long> {
}
