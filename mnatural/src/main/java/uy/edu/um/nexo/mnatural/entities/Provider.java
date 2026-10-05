package uy.edu.um.nexo.mnatural.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "provider")
public class Provider extends User {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    public Provider() {
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
}