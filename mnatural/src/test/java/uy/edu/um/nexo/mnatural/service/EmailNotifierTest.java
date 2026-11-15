package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.repository.BookingNotificationRepository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los correos automáticos salen cuando se confirma la transacción y, si algo falla, solo se registra.
// EmailService y los repositorios son mocks: no se manda nada ni se toca la base de datos
class EmailNotifierTest {

    private static final LocalDateTime START = LocalDateTime.of(2027, 1, 15, 9, 30);

    private final EmailService emailService = mock(EmailService.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final BookingNotificationRepository bookingRepository = mock(BookingNotificationRepository.class);

    private EmailNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new EmailNotifier(emailService, adminRepository, bookingRepository);
    }

    private static Admin admin(String name, String email) {
        Admin admin = new Admin();
        admin.setName(name);
        admin.setEmail(email);
        return admin;
    }

    private Booking bookingOf(Provider provider) {
        Business business = new Business();
        business.setProvider(provider);
        Activity activity = new Activity();
        activity.setBusiness(business);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        Booking booking = new Booking();
        booking.setActivitySchedule(schedule);
        return booking;
    }

    private static Provider provider() {
        Provider provider = new Provider();
        provider.setName("Luis");
        provider.setEmail("luis@ejemplo.com");
        return provider;
    }

    private static EmailEvents.BookingCancelled cancelled(EmailEvents.BookingCancelled.Cause cause) {
        return new EmailEvents.BookingCancelled(30L, "ana@ejemplo.com", "Ana", "MN-ABCD2345", "Cabalgata", START, 2,
                cause, new BigDecimal("750"), EmailEvents.BookingCancelled.RefundState.COMPLETED);
    }

    // ===== Cada evento manda su correo =====

    @Test
    void aNewTouristGetsTheWelcomeEmail() {
        notifier.onTouristRegistered(new EmailEvents.TouristRegistered("Ana", "ana@ejemplo.com"));

        verify(emailService).sendTouristWelcome("Ana", "ana@ejemplo.com");
    }

    @Test
    void aNewProviderGetsTheirEmailAndEveryAdminIsWarned() {
        when(adminRepository.findAll()).thenReturn(List.of(admin("Marta", "marta@mnatural.uy"),
                admin("Pablo", "pablo@mnatural.uy")));

        notifier.onProviderRegistered(new EmailEvents.ProviderRegistered("Luis Pérez", "luis@ejemplo.com", "Cabalgatas"));

        verify(emailService).sendProviderRegistered("Luis Pérez", "luis@ejemplo.com", "Cabalgatas");
        verify(emailService).sendAdminProviderPending("Marta", "marta@mnatural.uy", "Luis Pérez", "Cabalgatas");
        verify(emailService).sendAdminProviderPending("Pablo", "pablo@mnatural.uy", "Luis Pérez", "Cabalgatas");
    }

    @Test
    void thePendingFailedAndExpiredBookingEmailsGoToTheTourist() {
        var pending = new EmailEvents.BookingPending(30L, "ana@ejemplo.com", "Ana", "MN-A", "Cabalgata", START, 2,
                BigDecimal.TEN, START);
        var failed = new EmailEvents.PaymentFailed(30L, "ana@ejemplo.com", "Ana", "Cabalgata", false, "x", START);
        var expired = new EmailEvents.BookingExpired(30L, "ana@ejemplo.com", "Ana", "MN-A", "Cabalgata", START);

        notifier.onBookingPending(pending);
        notifier.onPaymentFailed(failed);
        notifier.onBookingExpired(expired);

        verify(emailService).sendBookingPending(pending);
        verify(emailService).sendPaymentFailed(failed);
        verify(emailService).sendBookingExpired(expired);
    }

    @Test
    void aRefundThatIsCompletedGetsItsEmail() {
        var refund = new EmailEvents.RefundCompleted(30L, "ana@ejemplo.com", "Ana", "MN-A", "Cabalgata",
                BigDecimal.TEN, null);

        notifier.onRefundCompleted(refund);

        verify(emailService).sendRefundCompleted(refund);
    }

    @Test
    void whenATouristCancelsBothTheTouristAndTheProviderAreWarned() {
        when(bookingRepository.findWithProviderById(30L)).thenReturn(Optional.of(bookingOf(provider())));
        var event = cancelled(EmailEvents.BookingCancelled.Cause.BY_TOURIST);

        notifier.onBookingCancelled(event);

        verify(emailService).sendBookingCancelled(event);
        verify(emailService).sendProviderBookingCancelled("Luis", "luis@ejemplo.com", event);
    }

    @Test
    void whenTheProviderSuspendsOnlyTheTouristIsWarned() {
        var event = cancelled(EmailEvents.BookingCancelled.Cause.SCHEDULE_SUSPENDED);

        notifier.onBookingCancelled(event);

        verify(emailService).sendBookingCancelled(event);
        verify(emailService, never()).sendProviderBookingCancelled(anyString(), anyString(), any());
        verify(bookingRepository, never()).findWithProviderById(any());
    }

    @Test
    void theProviderOfTheActivityIsToldAboutAConfirmedBooking() {
        when(bookingRepository.findWithProviderById(30L)).thenReturn(Optional.of(bookingOf(provider())));
        var confirmed = new BookingConfirmedEvent(30L, "ana@ejemplo.com", "Ana", "MN-A", "Cabalgata", START, 4,
                BigDecimal.TEN);

        notifier.onBookingConfirmed(confirmed);

        verify(emailService).sendProviderNewBooking("Luis", "luis@ejemplo.com", confirmed);
    }

    @Test
    void ifTheBookingCannotBeFoundNoProviderEmailIsSent() {
        when(bookingRepository.findWithProviderById(30L)).thenReturn(Optional.empty());

        notifier.onBookingConfirmed(new BookingConfirmedEvent(30L, "a@b.com", "Ana", "MN-A", "Cabalgata", START, 1,
                BigDecimal.TEN));

        verify(emailService, never()).sendProviderNewBooking(anyString(), anyString(), any());
    }

    // ===== Si algo falla, no se propaga =====

    @Test
    void nothingThatGoesWrongWhileSendingEverBreaksTheOperation() {
        doThrow(new IllegalStateException("correo caído")).when(emailService).sendTouristWelcome(anyString(), anyString());
        doThrow(new IllegalStateException("correo caído")).when(emailService).sendBookingPending(any());
        doThrow(new IllegalStateException("correo caído")).when(emailService).sendPaymentFailed(any());
        doThrow(new IllegalStateException("correo caído")).when(emailService).sendBookingExpired(any());
        doThrow(new IllegalStateException("correo caído")).when(emailService).sendBookingCancelled(any());
        doThrow(new IllegalStateException("correo caído")).when(emailService).sendRefundCompleted(any());
        when(adminRepository.findAll()).thenThrow(new IllegalStateException("base caída"));
        when(bookingRepository.findWithProviderById(any())).thenThrow(new IllegalStateException("base caída"));

        assertThatCode(() -> {
            notifier.onTouristRegistered(new EmailEvents.TouristRegistered("Ana", "a@b.com"));
            notifier.onProviderRegistered(new EmailEvents.ProviderRegistered("Luis", "l@b.com", "Cabalgatas"));
            notifier.onBookingPending(new EmailEvents.BookingPending(1L, "a@b.com", "Ana", "MN-A", "Act", START, 1,
                    BigDecimal.TEN, START));
            notifier.onPaymentFailed(new EmailEvents.PaymentFailed(1L, "a@b.com", "Ana", "Act", true, null, null));
            notifier.onBookingExpired(new EmailEvents.BookingExpired(1L, "a@b.com", "Ana", "MN-A", "Act", START));
            notifier.onBookingCancelled(cancelled(EmailEvents.BookingCancelled.Cause.BY_TOURIST));
            notifier.onRefundCompleted(new EmailEvents.RefundCompleted(1L, "a@b.com", "Ana", "MN-A", "Act",
                    BigDecimal.TEN, null));
            notifier.onBookingConfirmed(new BookingConfirmedEvent(1L, "a@b.com", "Ana", "MN-A", "Act", START, 1,
                    BigDecimal.TEN));
        }).doesNotThrowAnyException();
    }

    @Test
    void aFailureWarningOneAdminDoesNotStopTheProviderEmail() {
        when(adminRepository.findAll()).thenReturn(List.of(admin("Marta", "marta@mnatural.uy")));
        doThrow(new IllegalStateException("correo caído")).when(emailService)
                .sendAdminProviderPending(anyString(), anyString(), anyString(), anyString());

        assertThatCode(() -> notifier.onProviderRegistered(
                new EmailEvents.ProviderRegistered("Luis", "luis@ejemplo.com", "Cabalgatas"))).doesNotThrowAnyException();

        verify(emailService).sendProviderRegistered(eq("Luis"), eq("luis@ejemplo.com"), eq("Cabalgatas"));
    }

    // ===== Solo después del commit =====

    @Test
    void everyListenerRunsOnlyAfterTheTransactionCommits() {
        int listeners = 0;
        for (Method method : EmailNotifier.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("on")) {
                continue;
            }
            TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
            assertThat(listener).as(method.getName()).isNotNull();
            assertThat(listener.phase()).as(method.getName()).isEqualTo(TransactionPhase.AFTER_COMMIT);
            listeners++;
        }
        assertThat(listeners).isEqualTo(8);
    }
}
