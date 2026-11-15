package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.dto.ProviderScheduleDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario de la pantalla de horarios del prestador: repositorios y BookingService son mocks
class ActivityServiceTest {

    private static final String EMAIL = "prestador@ejemplo.test";
    private static final Long PROVIDER_ID = 3L;
    private static final Long ACTIVITY_ID = 10L;

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final ActivityScheduleRepository scheduleRepository = mock(ActivityScheduleRepository.class);
    private final BookingCancellationService cancellationService = mock(BookingCancellationService.class);

    private ActivityService service;
    private Activity activity;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository, scheduleRepository,
                mock(ActivityTypeRepository.class), userRepository, bookingService, cancellationService,
                mock(ActivityPhotoStorage.class));

        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(provider));

        activity = new Activity();
        activity.setId(ACTIVITY_ID);
        when(activityRepository.findByIdAndBusinessProviderId(ACTIVITY_ID, PROVIDER_ID))
                .thenReturn(Optional.of(activity));
    }

    private ActivitySchedule schedule(Long id, int capacity) {
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(id);
        schedule.setActivity(activity);
        schedule.setCapacity(capacity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(id));
        return schedule;
    }

    @Test
    void findSchedulesShowsCapacityAndTheFreeSpotsCalculatedByBookingService() {
        ActivitySchedule withBookings = schedule(1L, 10);
        ActivitySchedule full = schedule(2L, 4);
        when(activityRepository.findSchedulesOf(ACTIVITY_ID)).thenReturn(List.of(withBookings, full));
        when(bookingService.availableSpots(1L)).thenReturn(7);
        when(bookingService.availableSpots(2L)).thenReturn(0);

        List<ProviderScheduleDTO> result = service.findSchedules(EMAIL, ACTIVITY_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSchedule().getCapacity()).isEqualTo(10);
        assertThat(result.get(0).getAvailableSpots()).isEqualTo(7);
        assertThat(result.get(1).getSchedule().getCapacity()).isEqualTo(4);
        assertThat(result.get(1).getAvailableSpots()).isZero();
        // El cupo libre no se guarda en el horario: la capacidad queda igual
        assertThat(withBookings.getCapacity()).isEqualTo(10);
    }

    @Test
    void findSchedulesOfAnotherProvidersActivityFailsWithoutCountingSpots() {
        when(activityRepository.findByIdAndBusinessProviderId(99L, PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSchedules(EMAIL, 99L)).isInstanceOf(ActivityNotFoundException.class);
        verify(bookingService, never()).availableSpots(anyLong());
    }

    // ===== Suspender un horario =====

    @Test
    void suspendingAScheduleMarksItSuspendedAndCancelsItsBookingsWithFullRefund() {
        ActivitySchedule schedule = schedule(1L, 10);
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(cancellationService.cancelForSuspension(1L)).thenReturn(new SuspensionResult(2, 1, 0));

        SuspensionResult result = service.suspendSchedule(EMAIL, ACTIVITY_ID, 1L);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.SUSPENDED);
        assertThat(result.cancelledBookings()).isEqualTo(2);
        verify(cancellationService).cancelForSuspension(1L);
    }

    @Test
    void aScheduleOfAnotherActivityOrAlreadyStartedCannotBeSuspended() {
        Activity other = new Activity();
        other.setId(77L);
        ActivitySchedule foreign = schedule(1L, 10);
        foreign.setActivity(other);
        ActivitySchedule started = schedule(2L, 10);
        started.setStartDateTime(LocalDateTime.now().minusMinutes(5));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(foreign));
        when(scheduleRepository.findById(2L)).thenReturn(Optional.of(started));

        assertThatThrownBy(() -> service.suspendSchedule(EMAIL, ACTIVITY_ID, 1L))
                .isInstanceOf(ActivityValidationException.class);
        assertThatThrownBy(() -> service.suspendSchedule(EMAIL, ACTIVITY_ID, 2L))
                .isInstanceOf(ActivityValidationException.class);
        assertThat(started.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        verify(cancellationService, never()).cancelForSuspension(anyLong());
    }
}
