package uy.edu.um.nexo.mnatural.entities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tourist")
public class Tourist extends User{

    // Opcional: una condición a tener en cuenta (por ejemplo, muletas o silla de ruedas)
    @Column(nullable = true, columnDefinition = "TEXT")
    private String exceptionalCondition;

    public Tourist() {
    }

    public String getExceptionalCondition() {
        return exceptionalCondition;
    }

    public void setExceptionalCondition(String exceptionalCondition) {
        this.exceptionalCondition = exceptionalCondition;
    }
}
