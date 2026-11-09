package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import uy.edu.um.nexo.mnatural.dto.ProviderBookingDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.Participant;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.ProviderBookingRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Reservas del prestador: repositorio y resolver son mocks, no se toca la base de datos
class ProviderReservationServiceTest {

    private static final String EMAIL = "prestador@ejemplo.test";
    private static final Long PROVIDER_ID = 3L;

    private final ProviderBookingRepository bookingRepository = mock(ProviderBookingRepository.class);
    private final ApprovedProviderResolver resolver = mock(ApprovedProviderResolver.class);

    private ProviderReservationService service;

    @BeforeEach
    void setUp() {
        service = new ProviderReservationService(bookingRepository, resolver);
        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        when(resolver.require(EMAIL)).thenReturn(provider);
    }

    static Booking booking(BookingStatus status) {
        Activity activity = new Activity();
        activity.setId(10L);
        activity.setName("Trekking");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(20L);
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.of(2027, 1, 15, 9, 30));

        Tourist tourist = new Tourist();
        tourist.setName("Ana");
        tourist.setLastName("Pérez");
        tourist.setEmail("ana@ejemplo.com");
        tourist.setPhoneNumber("099111222");

        Participant participant = new Participant();
        participant.setId(1L);
        participant.setName("Luis");
        participant.setLastName("Gómez");
        participant.setDocumentType(DocumentType.CI);
        participant.setDocumentNumber("12345678");
        participant.setDateOfBirth(LocalDate.of(1990, 5, 1));
        participant.setNotes("Alérgico al maní");

        Booking booking = new Booking();
        booking.setId(5L);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setStatus(status);
        booking.setBookingCode("MN-ABCD2345");
        booking.setParticipantCount(1);
        booking.setGeneralNotes("Llegamos en auto");
        booking.addParticipant(participant);
        return booking;
    }

    @Test
    void withoutFiltersAllTheReservationsOfTheProviderAreListed() {
        when(bookingRepository.findAllOfProvider(PROVIDER_ID)).thenReturn(List.of(booking(BookingStatus.CONFIRMED)));

        List<ProviderBookingDTO> result = service.findReservations(EMAIL, null, null);

        assertThat(result).hasSize(1);
        verify(bookingRepository, never()).findByActivityOfProvider(PROVIDER_ID, 10L);
    }

    @Test
    void filteringByActivityUsesTheActivityQuery() {
        when(bookingRepository.findByActivityOfProvider(PROVIDER_ID, 10L)).thenReturn(List.of(booking(BookingStatus.CONFIRMED)));

        assertThat(service.findReservations(EMAIL, 10L, null)).hasSize(1);
    }

    @Test
    void filteringByScheduleTakesPrecedenceOverTheActivity() {
        when(bookingRepository.findByScheduleOfProvider(PROVIDER_ID, 20L)).thenReturn(List.of(booking(BookingStatus.CONFIRMED)));

        assertThat(service.findReservations(EMAIL, 10L, 20L)).hasSize(1);
        verify(bookingRepository, never()).findByActivityOfProvider(PROVIDER_ID, 10L);
    }

    @Test
    void aConfirmedReservationShowsCodeScheduleHolderContactAndParticipants() {
        when(bookingRepository.findAllOfProvider(PROVIDER_ID)).thenReturn(List.of(booking(BookingStatus.CONFIRMED)));

        ProviderBookingDTO dto = service.findReservations(EMAIL, null, null).getFirst();

        assertThat(dto.code()).isEqualTo("MN-ABCD2345");
        assertThat(dto.statusLabel()).isEqualTo("Confirmada");
        assertThat(dto.activityName()).isEqualTo("Trekking");
        assertThat(dto.startDateTime()).isEqualTo(LocalDateTime.of(2027, 1, 15, 9, 30));
        assertThat(dto.participantCount()).isEqualTo(1);
        assertThat(dto.personalDataVisible()).isTrue();
        assertThat(dto.holderName()).isEqualTo("Ana Pérez");
        assertThat(dto.holderEmail()).isEqualTo("ana@ejemplo.com");
        assertThat(dto.holderPhone()).isEqualTo("099111222");
        assertThat(dto.generalNotes()).isEqualTo("Llegamos en auto");
        assertThat(dto.participants()).hasSize(1);
        ProviderBookingDTO.ParticipantRow participant = dto.participants().getFirst();
        assertThat(participant.name()).isEqualTo("Luis");
        assertThat(participant.lastName()).isEqualTo("Gómez");
        assertThat(participant.documentType()).isEqualTo("CI");
        assertThat(participant.documentNumber()).isEqualTo("12345678");
        assertThat(participant.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 1));
        assertThat(participant.notes()).isEqualTo("Alérgico al maní");
    }

    @Test
    void contactAndPersonalDataAreHiddenUnlessTheReservationIsConfirmed() {
        for (BookingStatus status : List.of(BookingStatus.PENDING, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
            when(bookingRepository.findAllOfProvider(PROVIDER_ID)).thenReturn(List.of(booking(status)));

            ProviderBookingDTO dto = service.findReservations(EMAIL, null, null).getFirst();

            assertThat(dto.personalDataVisible()).as(status.name()).isFalse();
            assertThat(dto.holderName()).as(status.name()).isNull();
            assertThat(dto.holderEmail()).as(status.name()).isNull();
            assertThat(dto.holderPhone()).as(status.name()).isNull();
            assertThat(dto.generalNotes()).as(status.name()).isNull();
            assertThat(dto.participants()).as(status.name()).isEmpty();
            // Lo que sí se ve para organizarse: estado, fecha y cantidad de participantes
            assertThat(dto.participantCount()).isEqualTo(1);
        }
    }

    @Test
    void anUnapprovedProviderCannotListReservations() {
        when(resolver.require("otro@ejemplo.test")).thenThrow(new AccessDeniedException("no aprobado"));

        assertThatThrownBy(() -> service.findReservations("otro@ejemplo.test", null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).findAllOfProvider(PROVIDER_ID);
    }
}
