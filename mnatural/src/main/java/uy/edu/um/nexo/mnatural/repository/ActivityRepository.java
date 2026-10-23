package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.Activity;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
}
