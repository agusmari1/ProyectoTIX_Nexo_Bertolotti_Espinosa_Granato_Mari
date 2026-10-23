package uy.edu.um.nexo.mnatural.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "provider")
public class Provider extends User {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    // Cuándo se registró; define el orden de llegada en la lista de pendientes.
    // El default de la base hace que Hibernate pueda agregar la columna aunque ya haya
    // prestadores cargados (sin él, un NOT NULL sobre filas existentes falla)
    @Column(nullable = false, updatable = false, columnDefinition = "timestamp(6) default now()")
    private LocalDateTime requestedAt = LocalDateTime.now();

    // Cuándo y quién resolvió la solicitud (aprobar o rechazar). Null mientras está pendiente
    @Column(nullable = true)
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id", nullable = true)
    private Admin reviewedBy;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "provider")
    private List<Business> businesses = new ArrayList<>();

    public Provider() {
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Admin getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Admin reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public List<Business> getBusinesses() {
        return businesses;
    }

    public void setBusinesses(List<Business> businesses) {
        this.businesses = businesses;
    }
}