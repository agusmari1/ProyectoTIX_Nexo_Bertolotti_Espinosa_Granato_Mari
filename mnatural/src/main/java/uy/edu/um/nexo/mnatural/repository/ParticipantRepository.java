package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
}
