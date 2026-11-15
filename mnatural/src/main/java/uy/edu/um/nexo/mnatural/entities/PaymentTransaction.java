package uy.edu.um.nexo.mnatural.entities;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Un intento de cobro (o, más adelante, de reembolso) de una reserva.
// Cada reintento del turista es una fila nueva con attemptNumber + 1, así queda el historial completo.
// Nunca se guarda el número completo de la tarjeta ni el CVV: solo la marca y los últimos 4 dígitos
@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // En pesos uruguayos
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type = PaymentType.CHARGE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // 1 para el primer intento de una reserva, 2 para el primer reintento, etc.
    @Column(nullable = false)
    private int attemptNumber;

    // Identificador con el que el servicio de pagos reconoce este intento.
    // Queda vacío si el servicio falló antes de devolverlo
    @Column(nullable = true, unique = true)
    private String externalReference;

    @Column(nullable = true, length = 500)
    private String failureReason;

    @Column(nullable = true)
    private String cardBrand;

    @Column(nullable = true, length = 4)
    private String cardLastFour;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Para un reembolso: el cobro que se está devolviendo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id", nullable = true)
    private PaymentTransaction originalPayment;

    public PaymentTransaction() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentType getType() {
        return type;
    }

    public void setType(PaymentType type) {
        this.type = type;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public PaymentTransaction getOriginalPayment() {
        return originalPayment;
    }

    public void setOriginalPayment(PaymentTransaction originalPayment) {
        this.originalPayment = originalPayment;
    }
}
