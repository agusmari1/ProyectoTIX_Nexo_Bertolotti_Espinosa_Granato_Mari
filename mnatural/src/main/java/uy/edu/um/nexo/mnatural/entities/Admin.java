package uy.edu.um.nexo.mnatural.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin")
public class Admin extends User {

    public Admin() {
    }
}
