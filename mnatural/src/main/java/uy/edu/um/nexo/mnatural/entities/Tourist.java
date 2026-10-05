package uy.edu.um.nexo.mnatural.entities;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tourist")
public class Tourist extends User{
    public Tourist() {
    }
}
