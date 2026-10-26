package uy.edu.um.nexo.mnatural.entities;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Turista titular de la reserva
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tourist_id", nullable = false)
    private Tourist tourist;

    // Fecha y horario concreto que se reserva
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_schedule_id", nullable = false)
    private ActivitySchedule activitySchedule;

    // Si el titular participa, también se carga como un Participant más,
    // así la validación de edad y su observación particular quedan en un solo lugar
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    @Column(nullable = false)
    private int participantCount;

    // false cuando el titular reserva solo en representación de terceros
    @Column(nullable = false)
    private boolean holderParticipates = true;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String generalNotes;

    // Precio por persona multiplicado por la cantidad de participantes, en pesos uruguayos
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    // Se genera al confirmarse la reserva, por eso puede quedar vacío mientras está pendiente
    @Column(nullable = true, unique = true)
    private String bookingCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Hasta cuándo quedan retenidos los cupos (10 minutos al elegirlos, hasta 2 horas si queda pendiente)
    @Column(nullable = true)
    private LocalDateTime heldUntil;

    public Booking() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Mantiene sincronizados los dos lados de la relación
    public void addParticipant(Participant participant) {
        participants.add(participant);
        participant.setBooking(this);
    }

    public void removeParticipant(Participant participant) {
        participants.remove(participant);
        participant.setBooking(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Tourist getTourist() {
        return tourist;
    }

    public void setTourist(Tourist tourist) {
        this.tourist = tourist;
    }

    public ActivitySchedule getActivitySchedule() {
        return activitySchedule;
    }

    public void setActivitySchedule(ActivitySchedule activitySchedule) {
        this.activitySchedule = activitySchedule;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public boolean isHolderParticipates() {
        return holderParticipates;
    }

    public void setHolderParticipates(boolean holderParticipates) {
        this.holderParticipates = holderParticipates;
    }

    public String getGeneralNotes() {
        return generalNotes;
    }

    public void setGeneralNotes(String generalNotes) {
        this.generalNotes = generalNotes;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getHeldUntil() {
        return heldUntil;
    }

    public void setHeldUntil(LocalDateTime heldUntil) {
        this.heldUntil = heldUntil;
    }
}
