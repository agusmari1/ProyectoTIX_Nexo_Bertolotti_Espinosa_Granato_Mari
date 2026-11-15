package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.dto.BookingRequestDTO;
import uy.edu.um.nexo.mnatural.dto.ProviderRegistrationDTO;
import uy.edu.um.nexo.mnatural.dto.TouristRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;
import uy.edu.um.nexo.mnatural.payment.CardPaymentRequest;
import uy.edu.um.nexo.mnatural.payment.CheckoutMode;
import uy.edu.um.nexo.mnatural.payment.GatewayPaymentStatus;
import uy.edu.um.nexo.mnatural.payment.GatewayRefundResult;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los servicios publican el evento que dispara cada correo, y solo cuando corresponde.
// Repositorios y servicio de pagos son mocks: no se toca la base de datos ni internet
class EmailEventsPublishingTest {

    private static final LocalDateTime START = LocalDateTime.now().plusDays(10);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private <T> List<T> published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
    }

    // ===== Registro del turista y del prestador =====

    @Test
    void registeringATouristPublishesTheWelcomeEvent() {
        UserRepository userRepository = mock(UserRepository.class);
        TouristRepository touristRepository = mock(TouristRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(touristRepository.save(any(Tourist.class))).thenAnswer(inv -> inv.getArgument(0));
        TouristRegistrationDTO dto = new TouristRegistrationDTO();
        dto.setName("Ana");
        dto.setLastName("Prueba");
        dto.setDateOfBirth(LocalDate.of(1995, 5, 10));
        dto.setDocumentType(DocumentType.CI);
        dto.setDocumentNumber("12345678");
        dto.setCountry("Uruguay");
        dto.setEmail("  Ana@Ejemplo.Test ");
        dto.setPassword("clave-de-prueba");
        dto.setConfirmPassword("clave-de-prueba");

        new TouristService(userRepository, touristRepository, encoder, publisher).register(dto);

        List<EmailEvents.TouristRegistered> events = published(EmailEvents.TouristRegistered.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().name()).isEqualTo("Ana");
        // El correo es el ya normalizado, el mismo con el que se guardó la cuenta
        assertThat(events.getFirst().email()).isEqualTo("ana@ejemplo.test");
    }

    @Test
    void aTouristWhoIsAlreadyRegisteredGetsNoWelcomeEmail() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByEmail("ana@ejemplo.test")).thenReturn(true);
        TouristRegistrationDTO dto = new TouristRegistrationDTO();
        dto.setEmail("ana@ejemplo.test");

        assertThatCode(() -> new TouristService(userRepository, mock(TouristRepository.class),
                mock(PasswordEncoder.class), publisher).register(dto)).isInstanceOf(RuntimeException.class);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void registeringAProviderPublishesTheEventWithTheBusinessName() {
        UserRepository userRepository = mock(UserRepository.class);
        ProviderRepository providerRepository = mock(ProviderRepository.class);
        BusinessRepository businessRepository = mock(BusinessRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        ProviderRegistrationDTO dto = new ProviderRegistrationDTO();
        dto.setName("Pablo");
        dto.setLastName("Prueba");
        dto.setDateOfBirth(LocalDate.of(1985, 3, 2));
        dto.setDocumentType(DocumentType.CI);
        dto.setDocumentNumber("12345678");
        dto.setCountry("Uruguay");
        dto.setEmail("prestador@ejemplo.test");
        dto.setPassword("clave-de-prueba");
        dto.setConfirmPassword("clave-de-prueba");
        dto.setBusinessName("Cabalgatas del Sur");
        dto.setLegalType(LegalType.INDIVIDUAL);
        dto.setAddress("Calle de Prueba 123");
        dto.setBusinessPhone("099000000");
        dto.setBusinessEmail("emprendimiento@ejemplo.test");

        new ProviderService(userRepository, providerRepository, businessRepository, encoder, publisher).register(dto);

        List<EmailEvents.ProviderRegistered> events = published(EmailEvents.ProviderRegistered.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().name()).isEqualTo("Pablo");
        assertThat(events.getFirst().email()).isEqualTo("prestador@ejemplo.test");
        assertThat(events.getFirst().businessName()).isEqualTo("Cabalgatas del Sur");
    }

    // ===== Reserva creada =====

    @Test
    void creatingABookingPublishesThePendingEventWithTheDeadlineToPay() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        ActivityScheduleRepository scheduleRepository = mock(ActivityScheduleRepository.class);
        Activity activity = new Activity();
        activity.setId(10L);
        activity.setName("Cabalgata");
        activity.setPricePerPerson(new BigDecimal("500.00"));
        activity.setStatus(ActivityStatus.PUBLISHED);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(1L);
        schedule.setActivity(activity);
        schedule.setStartDateTime(START);
        schedule.setCapacity(5);
        when(bookingRepository.findScheduleForUpdate(1L)).thenReturn(Optional.of(schedule));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        Tourist tourist = new Tourist();
        tourist.setName("Ana");
        tourist.setLastName("Prueba");
        tourist.setEmail("ana@ejemplo.test");
        tourist.setDocumentType(DocumentType.CI);
        tourist.setDocumentNumber("00000000");
        tourist.setDateOfBirth(LocalDate.of(1990, 1, 1));
        BookingRequestDTO dto = new BookingRequestDTO();
        dto.setActivityScheduleId(1L);
        dto.setHolderParticipates(true);

        Booking booking = new BookingService(bookingRepository, scheduleRepository, publisher).createBooking(tourist, dto);

        List<EmailEvents.BookingPending> events = published(EmailEvents.BookingPending.class);
        assertThat(events).hasSize(1);
        EmailEvents.BookingPending event = events.getFirst();
        assertThat(event.touristEmail()).isEqualTo("ana@ejemplo.test");
        assertThat(event.bookingCode()).isEqualTo(booking.getBookingCode()).startsWith("MN-");
        assertThat(event.activityName()).isEqualTo("Cabalgata");
        assertThat(event.participantCount()).isEqualTo(1);
        assertThat(event.totalAmount()).isEqualByComparingTo("500.00");
        assertThat(event.payBefore()).isEqualTo(booking.getHeldUntil());
    }

    // ===== Pagos =====

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private Booking booking;
    private PaymentTransaction charge;
    private PaymentService payments;

    @BeforeEach
    void setUpPayments() {
        payments = new PaymentService(repository, gateway, publisher);
        when(gateway.checkoutMode()).thenReturn(CheckoutMode.CARD_FORM);
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByBookingIdAndTypeAndStatus(30L, PaymentType.CHARGE, PaymentStatus.PENDING))
                .thenReturn(List.of());

        Tourist tourist = new Tourist();
        tourist.setId(7L);
        tourist.setName("Ana");
        tourist.setEmail("ana@ejemplo.test");
        Activity activity = new Activity();
        activity.setName("Cabalgata");
        activity.setBookingCutoffMinutes(0);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(START);
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);
        booking = new Booking();
        booking.setId(30L);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setBookingCode("MN-ABCD2345");
        booking.setTotalAmount(new BigDecimal("3000.00"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(createdAt);
        booking.setHeldUntil(createdAt.plusMinutes(10));
        when(repository.findBookingForUpdate(30L)).thenReturn(Optional.of(booking));

        charge = new PaymentTransaction();
        charge.setId(100L);
        charge.setBooking(booking);
        charge.setType(PaymentType.CHARGE);
        charge.setAmount(booking.getTotalAmount());
        charge.setStatus(PaymentStatus.PENDING);
        charge.setAttemptNumber(1);
        charge.setExternalReference("MN-30-abc");
        when(repository.findByExternalReference("MN-30-abc")).thenReturn(Optional.of(charge));
    }

    private static CardPaymentRequest card() {
        return new CardPaymentRequest("tok", "master", null, 1, null, null, null, null, null, null, null);
    }

    private static GatewayPaymentStatus status(PaymentStatus status, String reason) {
        return new GatewayPaymentStatus(status, "master", "0604", reason, new BigDecimal("3000.00"), "UYU");
    }

    @Test
    void aRejectedCardPaymentPublishesTheFailedEventWithTheRetryDeadline() {
        when(gateway.payWithCard(any(), anyString(), any(), any()))
                .thenReturn(status(PaymentStatus.REJECTED, "Fondos insuficientes."));

        payments.payWithCard(30L, 7L, card());

        List<EmailEvents.PaymentFailed> events = published(EmailEvents.PaymentFailed.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().error()).isFalse();
        assertThat(events.getFirst().reason()).isEqualTo("Fondos insuficientes.");
        assertThat(events.getFirst().touristEmail()).isEqualTo("ana@ejemplo.test");
        assertThat(events.getFirst().activityName()).isEqualTo("Cabalgata");
        // La reserva sigue pendiente: se le dice hasta cuándo puede reintentar
        assertThat(events.getFirst().payBefore()).isAfter(LocalDateTime.now());
    }

    @Test
    void anApprovedOrInProcessPaymentPublishesNoFailureEvent() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(status(PaymentStatus.PENDING, "En proceso."));

        payments.payWithCard(30L, 7L, card());

        assertThat(published(EmailEvents.PaymentFailed.class)).isEmpty();
    }

    @Test
    void whenThePaymentServiceDoesNotAnswerAnErrorEventIsPublished() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenThrow(new PaymentGatewayException("timeout"));

        payments.payWithCard(30L, 7L, card());

        List<EmailEvents.PaymentFailed> events = published(EmailEvents.PaymentFailed.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().error()).isTrue();
    }

    @Test
    void theSameRejectionReportedAgainDoesNotRepeatTheEmail() {
        when(gateway.getPaymentStatus("MN-30-abc")).thenReturn(status(PaymentStatus.REJECTED, "Rechazada."));

        payments.syncByNotification("MN-30-abc");
        payments.syncByNotification("MN-30-abc");
        payments.syncByNotification("MN-30-abc");

        // Pasó de PENDING a REJECTED una sola vez: las otras dos veces ya estaba rechazado
        assertThat(published(EmailEvents.PaymentFailed.class)).hasSize(1);
    }

    @Test
    void aRejectionThatBecomesAnErrorIsAnnouncedAgainBecauseItChanged() {
        charge.setStatus(PaymentStatus.REJECTED);
        when(gateway.getPaymentStatus("MN-30-abc")).thenReturn(
                new GatewayPaymentStatus(PaymentStatus.APPROVED, "master", "0604", null, new BigDecimal("1.00"), "UYU"));

        payments.syncByNotification("MN-30-abc");

        List<EmailEvents.PaymentFailed> events = published(EmailEvents.PaymentFailed.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().error()).isTrue();
    }

    @Test
    void aFailureWhilePreparingTheEmailNeverBreaksThePayment() {
        // Sin turista no se puede armar el aviso, pero el pago rechazado se registra igual
        booking.setTourist(null);
        when(gateway.getPaymentStatus("MN-30-abc")).thenReturn(status(PaymentStatus.REJECTED, "Rechazada."));

        assertThatCode(() -> payments.syncByNotification("MN-30-abc")).doesNotThrowAnyException();

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(published(EmailEvents.PaymentFailed.class)).isEmpty();
    }

    // ===== Reembolsos =====

    private void paidAndExpired() {
        charge.setStatus(PaymentStatus.APPROVED);
        booking.setStatus(BookingStatus.EXPIRED);
        when(repository.existsByBookingIdAndTypeAndStatus(30L, PaymentType.CHARGE, PaymentStatus.APPROVED)).thenReturn(true);
        when(repository.findFirstByBookingIdAndTypeAndStatus(30L, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(charge));
    }

    @Test
    void anExpiredBookingThatIsRefundedPublishesTheRefundCompletedEventWithTheReason() {
        paidAndExpired();
        when(gateway.refund(anyString(), any(BigDecimal.class), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "ref-1", null));

        payments.refundIfPaidButNotConfirmed(30L);

        List<EmailEvents.RefundCompleted> events = published(EmailEvents.RefundCompleted.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().amount()).isEqualByComparingTo("3000.00");
        assertThat(events.getFirst().bookingCode()).isEqualTo("MN-ABCD2345");
        assertThat(events.getFirst().reason()).contains("ya había vencido");
    }

    @Test
    void aRefundThatFailsPublishesNothingYet() {
        paidAndExpired();
        when(gateway.refund(anyString(), any(BigDecimal.class), anyBoolean())).thenThrow(new PaymentGatewayException("caído"));

        payments.refundIfPaidButNotConfirmed(30L);

        assertThat(published(EmailEvents.RefundCompleted.class)).isEmpty();
    }

    @Test
    void whenARetriedRefundFinallyCompletesTheTouristIsToldOnce() {
        booking.setStatus(BookingStatus.CANCELLED);
        charge.setStatus(PaymentStatus.APPROVED);
        PaymentTransaction failed = new PaymentTransaction();
        failed.setBooking(booking);
        failed.setType(PaymentType.REFUND);
        failed.setStatus(PaymentStatus.ERROR);
        failed.setAmount(new BigDecimal("3000.00"));
        when(repository.findBookingIdsWithFailedRefunds()).thenReturn(List.of(30L));
        when(repository.findFirstByBookingIdAndTypeAndStatus(30L, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(charge));
        when(repository.findFirstByBookingIdAndTypeOrderByAttemptNumberDesc(30L, PaymentType.REFUND))
                .thenReturn(Optional.of(failed));
        when(gateway.refund(anyString(), any(BigDecimal.class), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "ref-2", null));

        assertThat(payments.retryFailedRefunds()).isEqualTo(1);

        List<EmailEvents.RefundCompleted> events = published(EmailEvents.RefundCompleted.class);
        assertThat(events).hasSize(1);
        // El motivo ya se contó en el aviso de la cancelación
        assertThat(events.getFirst().reason()).isNull();
    }

    @Test
    void aRetriedRefundThatFailsAgainPublishesNothing() {
        booking.setStatus(BookingStatus.CANCELLED);
        charge.setStatus(PaymentStatus.APPROVED);
        PaymentTransaction failed = new PaymentTransaction();
        failed.setBooking(booking);
        failed.setType(PaymentType.REFUND);
        failed.setStatus(PaymentStatus.ERROR);
        failed.setAmount(new BigDecimal("3000.00"));
        when(repository.findBookingIdsWithFailedRefunds()).thenReturn(List.of(30L));
        when(repository.findFirstByBookingIdAndTypeAndStatus(30L, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(charge));
        when(repository.findFirstByBookingIdAndTypeOrderByAttemptNumberDesc(30L, PaymentType.REFUND))
                .thenReturn(Optional.of(failed));
        when(gateway.refund(anyString(), any(BigDecimal.class), anyBoolean())).thenThrow(new PaymentGatewayException("caído"));

        assertThat(payments.retryFailedRefunds()).isZero();

        assertThat(published(EmailEvents.RefundCompleted.class)).isEmpty();
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
