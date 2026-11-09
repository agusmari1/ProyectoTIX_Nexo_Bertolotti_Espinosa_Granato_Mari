package uy.edu.um.nexo.mnatural.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.BookingRequestDTO;
import uy.edu.um.nexo.mnatural.dto.BookingSummaryDTO;
import uy.edu.um.nexo.mnatural.dto.BusinessContactDTO;
import uy.edu.um.nexo.mnatural.dto.ParticipantDTO;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Participant;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.BookingException;
import uy.edu.um.nexo.mnatural.exception.NotEnoughSpotsException;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    // Cuánto tiempo quedan retenidos los cupos mientras el turista paga (ESPECIFICACION.md, sección 3)
    public static final int HOLD_MINUTES = 10;

    // Sin 0/O ni 1/I, para que el código no se confunda al leerlo o dictarlo
    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final BookingRepository bookingRepository;
    private final ActivityScheduleRepository activityScheduleRepository;
    private final SecureRandom random = new SecureRandom();
    private final ApplicationEventPublisher eventPublisher;

    // Este es el que usa Spring. El correo "reserva pendiente de pago" sale recién cuando se confirma la transacción
    @Autowired
    public BookingService(BookingRepository bookingRepository,
                          ActivityScheduleRepository activityScheduleRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.activityScheduleRepository = activityScheduleRepository;
        this.eventPublisher = eventPublisher;
    }

    // Sin avisos por correo (para los tests que no lo necesitan)
    public BookingService(BookingRepository bookingRepository,
                          ActivityScheduleRepository activityScheduleRepository) {
        this(bookingRepository, activityScheduleRepository, event -> { });
    }

    // Cupos libres de una fecha y horario, para mostrarlos antes de reservar
    @Transactional(readOnly = true)
    public int availableSpots(Long activityScheduleId) {
        ActivitySchedule schedule = activityScheduleRepository.findById(activityScheduleId)
                .orElseThrow(() -> new BookingException("La fecha elegida no existe."));
        return availableSpots(schedule, LocalDateTime.now());
    }

    // Crea la reserva en PENDING y retiene los cupos por HOLD_MINUTES minutos.
    // Todo pasa en una sola transacción: si una validación falla, no se guarda nada
    @Transactional
    public Booking createBooking(Tourist tourist, BookingRequestDTO dto) {
        List<Participant> participants = buildParticipants(tourist, dto);
        if (participants.isEmpty()) {
            throw new BookingException("Agregá al menos un participante.");
        }

        // Primero se bloquea la fila del horario y recién después se cuentan los lugares.
        // Si dos turistas reservan la misma fecha al mismo tiempo, el segundo espera acá
        // hasta que termine el primero, y cuando cuenta ya ve la reserva nueva
        ActivitySchedule schedule = lockSchedule(dto.getActivityScheduleId());
        Activity activity = schedule.getActivity();
        LocalDateTime now = LocalDateTime.now();

        validateBookable(schedule, activity, now);
        for (Participant participant : participants) {
            validateMinAge(participant, activity, schedule);
        }

        int available = availableSpots(schedule, now);
        if (participants.size() > available) {
            throw new NotEnoughSpotsException(available, participants.size());
        }

        Booking booking = new Booking();
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setHolderParticipates(dto.isHolderParticipates());
        booking.setGeneralNotes(blankToNull(dto.getGeneralNotes()));
        booking.setParticipantCount(participants.size());
        booking.setTotalAmount(activity.getPricePerPerson().multiply(BigDecimal.valueOf(participants.size())));
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingCode(generateUniqueCode());
        booking.setHeldUntil(now.plusMinutes(HOLD_MINUTES));
        participants.forEach(booking::addParticipant);

        Booking saved = bookingRepository.save(booking);
        // Aviso al turista: reserva creada, pendiente de pago, con el plazo para pagar
        eventPublisher.publishEvent(new EmailEvents.BookingPending(saved.getId(), tourist.getEmail(), tourist.getName(),
                saved.getBookingCode(), activity.getName(), schedule.getStartDateTime(), participants.size(),
                saved.getTotalAmount(), saved.getHeldUntil()));
        return saved;
    }

    // Lo llama BookingExpirationTask cada minuto. Devuelve cuántas reservas vencieron
    @Transactional
    public int expireHeldBookings() {
        return bookingRepository.expireHeldBookings(LocalDateTime.now());
    }

    // Lo que se muestra antes de reservar una fecha, con el motivo si no se puede reservar
    @Transactional(readOnly = true)
    public BookingSummaryDTO getBookingSummary(Long activityScheduleId) {
        ActivitySchedule schedule = activityScheduleRepository.findById(activityScheduleId)
                .orElseThrow(() -> new BookingException("La fecha elegida no existe."));
        Activity activity = schedule.getActivity();
        LocalDateTime now = LocalDateTime.now();
        int spots = availableSpots(schedule, now);
        return new BookingSummaryDTO(schedule.getId(), activity.getId(), activity.getName(),
                activity.getBusiness().getName(), activity.getLocality(), schedule.getStartDateTime(),
                activity.getDurationMinutes(), activity.getPricePerPerson(), activity.getMinAge(), spots,
                HOLD_MINUTES, blockedReason(schedule, activity, spots, now));
    }

    // Las reservas del turista, de la más reciente a la más antigua
    @Transactional(readOnly = true)
    public List<TouristBookingDTO> findBookingsOf(Long touristId) {
        LocalDateTime now = LocalDateTime.now();
        return bookingRepository.findByTouristIdOrderByCreatedAtDescIdDesc(touristId).stream()
                .map(booking -> toTouristBooking(booking, now))
                .toList();
    }

    // Solo hay datos de contacto con la reserva confirmada (null en cualquier otro caso).
    // Lo usa payments/booking.html, que ya verificó que la reserva es del turista logueado
    @Transactional(readOnly = true)
    public BusinessContactDTO findBusinessContact(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .map(booking -> booking.getActivitySchedule().getActivity().getBusiness())
                .map(business -> new BusinessContactDTO(business.getName(), business.getPhoneNumber(),
                        business.getEmail(), business.getAddress()))
                .orElse(null);
    }

    private TouristBookingDTO toTouristBooking(Booking booking, LocalDateTime now) {
        ActivitySchedule schedule = booking.getActivitySchedule();
        Activity activity = schedule.getActivity();
        boolean confirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        boolean finished = schedule.getStartDateTime().plusMinutes(activity.getDurationMinutes()).isBefore(now);
        return new TouristBookingDTO(booking.getId(), activity.getName(), schedule.getStartDateTime(),
                booking.getParticipantCount(), booking.getTotalAmount(), booking.getStatus(),
                booking.getHeldUntil(), confirmed ? booking.getBookingCode() : null, confirmed && finished);
    }

    // Usa las mismas validaciones que createBooking, para que el motivo sea siempre el mismo
    private String blockedReason(ActivitySchedule schedule, Activity activity, int spots, LocalDateTime now) {
        try {
            validateBookable(schedule, activity, now);
        } catch (BookingException e) {
            return e.getMessage();
        }
        return spots == 0 ? new NotEnoughSpotsException(0, 1).getMessage() : null;
    }

    private int availableSpots(ActivitySchedule schedule, LocalDateTime now) {
        long taken = bookingRepository.countTakenSpots(schedule.getId(), now);
        return (int) Math.max(0, schedule.getCapacity() - taken);
    }

    private ActivitySchedule lockSchedule(Long activityScheduleId) {
        if (activityScheduleId == null) {
            throw new BookingException("Elegí una fecha y horario.");
        }
        return bookingRepository.findScheduleForUpdate(activityScheduleId)
                .orElseThrow(() -> new BookingException("La fecha elegida no existe."));
    }

    private void validateBookable(ActivitySchedule schedule, Activity activity, LocalDateTime now) {
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            throw new BookingException("Esta actividad no está disponible para reservar.");
        }
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw new BookingException("Esta fecha no está disponible para reservar.");
        }

        // Con 0 minutos, el límite es la propia hora de inicio
        int cutoffMinutes = activity.getBookingCutoffMinutes();
        LocalDateTime deadline = schedule.getStartDateTime().minusMinutes(cutoffMinutes);
        if (!now.isBefore(deadline)) {
            throw new BookingException(cutoffMinutes == 0
                    ? "Esta fecha ya comenzó, no se puede reservar."
                    : "Ya pasó el plazo para reservar esta fecha: las reservas cierran "
                    + cutoffMinutes + " minutos antes del inicio.");
        }
    }

    // La edad se calcula al día de la actividad, no al día en que se reserva
    private void validateMinAge(Participant participant, Activity activity, ActivitySchedule schedule) {
        Integer minAge = activity.getMinAge();
        if (minAge == null || minAge == 0) {
            return;
        }
        int age = Period.between(participant.getDateOfBirth(), schedule.getStartDateTime().toLocalDate()).getYears();
        if (age < minAge) {
            throw new BookingException(participant.getName() + " " + participant.getLastName()
                    + " no cumple la edad mínima de " + minAge + " años para esta actividad.");
        }
    }

    // Si el titular participa, se carga como un participante más con sus propios datos
    private List<Participant> buildParticipants(Tourist tourist, BookingRequestDTO dto) {
        List<Participant> participants = new ArrayList<>();

        if (dto.isHolderParticipates()) {
            Participant holder = new Participant();
            holder.setName(tourist.getName());
            holder.setLastName(tourist.getLastName());
            holder.setDocumentType(tourist.getDocumentType());
            holder.setDocumentNumber(tourist.getDocumentNumber());
            holder.setDateOfBirth(tourist.getDateOfBirth());
            holder.setNotes(blankToNull(dto.getHolderNotes()));
            participants.add(holder);
        }

        if (dto.getParticipants() != null) {
            for (ParticipantDTO data : dto.getParticipants()) {
                participants.add(toParticipant(data));
            }
        }
        return participants;
    }

    private Participant toParticipant(ParticipantDTO data) {
        if (isBlank(data.getName()) || isBlank(data.getLastName()) || data.getDocumentType() == null
                || isBlank(data.getDocumentNumber()) || data.getDateOfBirth() == null) {
            throw new BookingException(
                    "Completá nombre, apellido, documento y fecha de nacimiento de cada participante.");
        }
        if (data.getDateOfBirth().isAfter(LocalDateTime.now().toLocalDate())) {
            throw new BookingException("La fecha de nacimiento no puede ser futura.");
        }

        Participant participant = new Participant();
        participant.setName(data.getName().trim());
        participant.setLastName(data.getLastName().trim());
        participant.setDocumentType(data.getDocumentType());
        participant.setDocumentNumber(data.getDocumentNumber().trim());
        participant.setDateOfBirth(data.getDateOfBirth());
        participant.setNotes(blankToNull(data.getNotes()));
        return participant;
    }

    // Por ejemplo MN-7KQ2XH4P. La columna booking_code es única, así que la base
    // también impide un repetido si dos se generaran iguales al mismo tiempo
    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder("MN-");
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
            }
            code = builder.toString();
        } while (bookingRepository.existsByBookingCode(code));
        return code;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
