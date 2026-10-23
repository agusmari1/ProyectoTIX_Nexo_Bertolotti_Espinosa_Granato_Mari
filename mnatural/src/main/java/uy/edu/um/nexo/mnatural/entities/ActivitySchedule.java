package uy.edu.um.nexo.mnatural.entities;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "activity_schedule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "start_date_time"})
)
public class ActivitySchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int availableSpots;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    public ActivitySchedule() {
    }

    // Al crear el horario todavía no hay reservas, así que todos los cupos están libres
    @PrePersist
    protected void onCreate() {
        availableSpots = capacity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getAvailableSpots() {
        return availableSpots;
    }

    public void setAvailableSpots(int availableSpots) {
        this.availableSpots = availableSpots;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }
}
