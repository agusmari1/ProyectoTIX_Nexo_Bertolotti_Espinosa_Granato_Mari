package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.ActivityType;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Long> {
}
