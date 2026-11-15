package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.BookingNotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Aviso al turista cuando su reserva vence. Repositorio, publicador y BookingService son mocks: no se toca la base
class BookingExpiryNotificationTest {

    private final BookingNotificationRepository repository = mock(BookingNotificationRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final BookingService bookingService = mock(BookingService.class);

    private BookingExpiryNotifier notifier;
    private BookingExpirationTask task;

    @BeforeEach
    void setUp() {
        notifier = new BookingExpiryNotifier(repository, publisher);
        task = new BookingExpirationTask(bookingService, notifier);
    }

    private static Booking expired(long id, String email) {
        Tourist tourist = new Tourist();
        tourist.setName("Ana");
        tourist.setEmail(email);
        Activity activity = new Activity();
        activity.setName("Cabalgata");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.of(2027, 1, 15, 9, 30));
        Booking booking = new Booking();
        booking.setId(id);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setBookingCode("MN-" + id);
        return booking;
    }

    // ===== BookingExpiryNotifier =====

    @Test
    void theBookingsAboutToExpireAreCheckedWithAShortMargin() {
        when(repository.findIdsHeldUntilBefore(any())).thenReturn(List.of(1L));
        LocalDateTime before = LocalDateTime.now();

        List<Long> ids = notifier.findExpiring();

        assertThat(ids).containsExactly(1L);
        ArgumentCaptor<LocalDateTime> limit = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findIdsHeldUntilBefore(limit.capture());
        assertThat(limit.getValue()).isAfter(before.plusSeconds(BookingExpiryNotifier.MARGIN_SECONDS - 5))
                .isBefore(LocalDateTime.now().plusSeconds(BookingExpiryNotifier.MARGIN_SECONDS + 5));
    }

    @Test
    void anEventIsPublishedForEachBookingThatReallyExpired() {
        when(repository.findExpiredByIds(anyCollection())).thenReturn(List.of(expired(1L, "ana@ejemplo.com"),
                expired(2L, "luis@ejemplo.com")));

        int count = notifier.publishExpired(List.of(1L, 2L, 3L));

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).hasSize(2).allSatisfy(e -> assertThat(e).isInstanceOf(EmailEvents.BookingExpired.class));
        EmailEvents.BookingExpired first = (EmailEvents.BookingExpired) events.getAllValues().getFirst();
        assertThat(first.touristEmail()).isEqualTo("ana@ejemplo.com");
        assertThat(first.bookingCode()).isEqualTo("MN-1");
        assertThat(first.activityName()).isEqualTo("Cabalgata");
    }

    @Test
    void aBookingThatWasPaidInTheMeantimeIsNotAnnounced() {
        // La consulta solo devuelve las que quedaron EXPIRED: la 3 se confirmó antes del UPDATE
        when(repository.findExpiredByIds(anyCollection())).thenReturn(List.of(expired(1L, "ana@ejemplo.com")));

        assertThat(notifier.publishExpired(List.of(1L, 3L))).isEqualTo(1);
    }

    @Test
    void withNothingToCheckNothingIsQueriedOrPublished() {
        assertThat(notifier.publishExpired(List.of())).isZero();
        assertThat(notifier.publishExpired(null)).isZero();

        verify(repository, never()).findExpiredByIds(anyCollection());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    // ===== BookingExpirationTask =====

    @Test
    void theBookingsAreNotedBeforeExpiringThemAndAnnouncedAfter() {
        when(repository.findIdsHeldUntilBefore(any())).thenReturn(List.of(1L));
        when(bookingService.expireHeldBookings()).thenReturn(1);
        when(repository.findExpiredByIds(anyCollection())).thenReturn(List.of(expired(1L, "ana@ejemplo.com")));

        task.expireHeldBookings();

        InOrder order = inOrder(repository, bookingService, publisher);
        order.verify(repository).findIdsHeldUntilBefore(any());
        order.verify(bookingService).expireHeldBookings();
        order.verify(repository).findExpiredByIds(List.of(1L));
        order.verify(publisher).publishEvent(any(EmailEvents.BookingExpired.class));
    }

    @Test
    void ifNothingExpiredNobodyIsAnnounced() {
        when(repository.findIdsHeldUntilBefore(any())).thenReturn(List.of(1L));
        when(bookingService.expireHeldBookings()).thenReturn(0);

        task.expireHeldBookings();

        verify(repository, never()).findExpiredByIds(anyCollection());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aFailureWhileAnnouncingNeverBreaksTheExpirationTask() {
        when(repository.findIdsHeldUntilBefore(any())).thenReturn(List.of(1L));
        when(bookingService.expireHeldBookings()).thenReturn(1);
        doThrow(new IllegalStateException("base caída")).when(repository).findExpiredByIds(anyCollection());

        assertThatCode(() -> task.expireHeldBookings()).doesNotThrowAnyException();
    }
}
