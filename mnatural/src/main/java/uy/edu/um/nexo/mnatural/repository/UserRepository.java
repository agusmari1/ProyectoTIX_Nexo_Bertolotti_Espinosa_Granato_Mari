package uy.edu.um.nexo.mnatural.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.nexo.mnatural.entities.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    // Usado por el login. Devuelve la subclase real (Admin, Provider o Tourist)
    Optional<User> findByEmail(String email);
}
