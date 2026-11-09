package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.BookingException;
import uy.edu.um.nexo.mnatural.exception.NotEnoughSpotsException;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Las reglas de la reserva, sin base de datos. El bloqueo de fila y las consultas de cupos
// reales se prueban en BookingServiceConcurrencyTest
class BookingServiceTest {

    private static final long SCHEDULE_ID = 1L;

    private BookingRepository bookingRepository;
    private ActivityScheduleRepository scheduleRepository;
    private BookingService service;
    private Activity activity;
    private ActivitySchedule schedule;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        scheduleRepository = mock(ActivityScheduleRepository.class);
        service = new BookingService(bookingRepository, scheduleRepository);

        Business business = new Business();
        business.setName("Emprendimiento");
        business.setPhoneNumber("099000000");
        business.setEmail("contacto@ejemplo.test");
        business.setAddress("Calle de Prueba 123");

        activity = new Activity();
        activity.setId(10L);
        activity.setName("Cabalgata");
        activity.setLocality("Minas");
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal("500.00"));
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setBusiness(business);

        schedule = new ActivitySchedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(7));
        schedule.setCapacity(5);

        when(bookingRepository.findScheduleForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsPendingBookingWithHoldAndTotal() {
        Booking booking = service.createBooking(tourist(), request(3));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getParticipantCount()).isEqualTo(3);
        assertThat(booking.getParticipants()).hasSize(3);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("1500.00");
        assertThat(booking.getBookingCode()).startsWith("MN-");
        assertThat(booking.getHeldUntil()).isAfter(LocalDateTime.now().plusMinutes(9));
    }

    @Test
    void holderWhoDoesNotParticipateIsNotCounted() {
        BookingRequestDTO dto = request(3);
        dto.setHolderParticipates(false);

        Booking booking = service.createBooking(tourist(), dto);

        assertThat(booking.getParticipantCount()).isEqualTo(2);
        assertThat(booking.getParticipants()).extracting("name").containsOnly("Acompañante");
    }

    @Test
    void cannotBookWithoutParticipants() {
        BookingRequestDTO dto = request(1);
        dto.setHolderParticipates(false);

        assertThatThrownBy(() -> service.createBooking(tourist(), dto))
                .isInstanceOf(BookingException.class)
                .hasMessage("Agregá al menos un participante.");
    }

    @Test
    void cannotBookMoreSpotsThanAvailable() {
        when(bookingRepository.countTakenSpots(anyLong(), any())).thenReturn(4L);

        assertThatThrownBy(() -> service.createBooking(tourist(), request(2)))
                .isInstanceOf(NotEnoughSpotsException.class)
                .hasMessage("Solo quedan 1 lugares disponibles para esta fecha y pediste 2.");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cannotBookWhenTheScheduleIsSuspended() {
        schedule.setStatus(ScheduleStatus.SUSPENDED);

        assertThatThrownBy(() -> service.createBooking(tourist(), request(1)))
                .isInstanceOf(BookingException.class)
                .hasMessage("Esta fecha no está disponible para reservar.");
    }

    @Test
    void cannotBookWhenTheActivityIsNotPublished() {
        activity.setStatus(ActivityStatus.SUSPENDED);

        assertThatThrownBy(() -> service.createBooking(tourist(), request(1)))
                .isInstanceOf(BookingException.class)
                .hasMessage("Esta actividad no está disponible para reservar.");
    }

    @Test
    void cannotBookAfterTheBookingDeadline() {
        activity.setBookingCutoffMinutes(60);
        schedule.setStartDateTime(LocalDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> service.createBooking(tourist(), request(1)))
                .isInstanceOf(BookingException.class)
                .hasMessage("Ya pasó el plazo para reservar esta fecha: las reservas cierran 60 minutos antes del inicio.");
    }

    @Test
    void cannotBookAStartedScheduleWithoutCutoff() {
        schedule.setStartDateTime(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.createBooking(tourist(), request(1)))
                .isInstanceOf(BookingException.class)
                .hasMessage("Esta fecha ya comenzó, no se puede reservar.");
    }

    @Test
    void everyParticipantMustReachTheMinimumAge() {
        activity.setMinAge(18);
        BookingRequestDTO dto = request(2);
        dto.getParticipants().get(0).setDateOfBirth(LocalDate.now().minusYears(10));

        assertThatThrownBy(() -> service.createBooking(tourist(), dto))
                .isInstanceOf(BookingException.class)
                .hasMessage("Acompañante 1 no cumple la edad mínima de 18 años para esta actividad.");
    }

    @Test
    void participantsMustHaveTheirData() {
        BookingRequestDTO dto = request(2);
        dto.getParticipants().get(0).setDocumentNumber(" ");

        assertThatThrownBy(() -> service.createBooking(tourist(), dto))
                .isInstanceOf(BookingException.class)
                .hasMessage("Completá nombre, apellido, documento y fecha de nacimiento de cada participante.");
    }

    @Test
    void expireHeldBookingsReturnsHowManyExpired() {
        when(bookingRepository.expireHeldBookings(any())).thenReturn(3);

        assertThat(service.expireHeldBookings()).isEqualTo(3);
    }

    @Test
    void summaryOfABookableSchedule() {
        when(bookingRepository.countTakenSpots(anyLong(), any())).thenReturn(2L);

        BookingSummaryDTO summary = service.getBookingSummary(SCHEDULE_ID);

        assertThat(summary.isBookable()).isTrue();
        assertThat(summary.availableSpots()).isEqualTo(3);
        assertThat(summary.activityName()).isEqualTo("Cabalgata");
        assertThat(summary.holdMinutes()).isEqualTo(BookingService.HOLD_MINUTES);
    }

    @Test
    void summaryExplainsWhyItCannotBeBooked() {
        when(bookingRepository.countTakenSpots(anyLong(), any())).thenReturn(5L);
        assertThat(service.getBookingSummary(SCHEDULE_ID).blockedReason())
                .isEqualTo("No quedan lugares disponibles para esta fecha.");

        schedule.setStatus(ScheduleStatus.SUSPENDED);
        assertThat(service.getBookingSummary(SCHEDULE_ID).blockedReason())
                .isEqualTo("Esta fecha no está disponible para reservar.");
    }

    @Test
    void summaryOfAMissingScheduleFails() {
        when(scheduleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBookingSummary(99L))
                .isInstanceOf(BookingException.class)
                .hasMessage("La fecha elegida no existe.");
    }

    @Test
    void bookingCodeIsOnlyShownWhenConfirmed() {
        givenBookingsOfTourist(booking(BookingStatus.PENDING), booking(BookingStatus.CONFIRMED));

        List<TouristBookingDTO> bookings = service.findBookingsOf(7L);

        assertThat(bookings.get(0).bookingCode()).isNull();
        assertThat(bookings.get(1).bookingCode()).isEqualTo("MN-CODIGO");
    }

    @Test
    void onlyAConfirmedBookingOfAFinishedActivityCanBeReviewed() {
        // Empezó hace 2 horas y dura 60 minutos: ya terminó
        schedule.setStartDateTime(LocalDateTime.now().minusHours(2));
        givenBookingsOfTourist(booking(BookingStatus.CONFIRMED), booking(BookingStatus.CANCELLED));

        List<TouristBookingDTO> bookings = service.findBookingsOf(7L);

        assertThat(bookings.get(0).reviewable()).isTrue();
        assertThat(bookings.get(1).reviewable()).isFalse();
    }

    @Test
    void aConfirmedBookingCannotBeReviewedUntilTheActivityEnds() {
        // Empezó hace 30 minutos y dura 60: todavía no terminó
        schedule.setStartDateTime(LocalDateTime.now().minusMinutes(30));
        givenBookingsOfTourist(booking(BookingStatus.CONFIRMED));

        assertThat(service.findBookingsOf(7L).get(0).reviewable()).isFalse();
    }

    @Test
    void businessContactIsOnlyAvailableWhenConfirmed() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(booking(BookingStatus.PENDING)));

        assertThat(service.findBusinessContact(1L)).isEqualTo(new BusinessContactDTO(
                "Emprendimiento", "099000000", "contacto@ejemplo.test", "Calle de Prueba 123"));
        assertThat(service.findBusinessContact(2L)).isNull();
    }

    private void givenBookingsOfTourist(Booking... bookings) {
        when(bookingRepository.findByTouristIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(bookings));
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setActivitySchedule(schedule);
        booking.setStatus(status);
        booking.setBookingCode("MN-CODIGO");
        booking.setTotalAmount(new BigDecimal("500.00"));
        return booking;
    }

    // El titular participa y suma "totalParticipants - 1" acompañantes
    private BookingRequestDTO request(int totalParticipants) {
        BookingRequestDTO dto = new BookingRequestDTO();
        dto.setActivityScheduleId(SCHEDULE_ID);
        dto.setHolderParticipates(true);
        for (int i = 1; i < totalParticipants; i++) {
            ParticipantDTO participant = new ParticipantDTO();
            participant.setName("Acompañante");
            participant.setLastName(String.valueOf(i));
            participant.setDocumentType(DocumentType.CI);
            participant.setDocumentNumber("1234567" + i);
            participant.setDateOfBirth(LocalDate.of(1995, 5, 5));
            dto.getParticipants().add(participant);
        }
        return dto;
    }

    private Tourist tourist() {
        Tourist tourist = new Tourist();
        tourist.setName("Ana");
        tourist.setLastName("Prueba");
        tourist.setDocumentType(DocumentType.CI);
        tourist.setDocumentNumber("00000000");
        tourist.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return tourist;
    }
}
