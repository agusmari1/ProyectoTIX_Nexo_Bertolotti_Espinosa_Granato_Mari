package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.dto.ActivityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Editar y suspender una actividad completa. Todo con mocks: no toca la base de datos
class ActivityManagementServiceTest {

    private static final String EMAIL = "prestador@ejemplo.test";
    private static final Long PROVIDER_ID = 3L;
    private static final Long ACTIVITY_ID = 10L;

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActivityScheduleRepository scheduleRepository = mock(ActivityScheduleRepository.class);
    private final ActivityTypeRepository typeRepository = mock(ActivityTypeRepository.class);
    private final BookingCancellationService cancellationService = mock(BookingCancellationService.class);

    private ActivityService service;
    private Activity activity;
    private ActivityType hiking;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository, scheduleRepository, typeRepository, userRepository,
                mock(BookingService.class), cancellationService);

        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(provider));

        activity = new Activity();
        activity.setId(ACTIVITY_ID);
        activity.setName("Trekking");
        activity.setAddress("Plaza");
        activity.setLocality("Minas");
        activity.setDescription("Vieja descripción");
        activity.setPricePerPerson(new BigDecimal("500"));
        activity.setStatus(ActivityStatus.PUBLISHED);
        when(activityRepository.findByIdAndBusinessProviderId(ACTIVITY_ID, PROVIDER_ID))
                .thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        hiking = new ActivityType();
        hiking.setId(1L);
        hiking.setName("Senderismo");
        hiking.setEnabled(true);
        when(typeRepository.findAllById(Set.of(1L))).thenReturn(List.of(hiking));
    }

    private ActivityDTO validDto() {
        ActivityDTO dto = new ActivityDTO();
        dto.setName("Otro nombre que no debe aplicarse");
        dto.setAddress("Otra dirección");
        dto.setLocality("Otra localidad");
        dto.setDescription("  Nueva descripción  ");
        dto.setDurationMinutes(90);
        dto.setPricePerPerson(new BigDecimal("750"));
        dto.setMinAge(12);
        dto.setRequirements("  ");
        dto.setEquipment("Botas");
        dto.setBookingCutoffMinutes(60);
        dto.setCancellationAllowed(true);
        dto.setCancellationCutoffMinutes(1440);
        dto.setRefundPercentage(new BigDecimal("50"));
        dto.setActivityTypeIds(Set.of(1L));
        return dto;
    }

    private ActivitySchedule schedule(Long id, ScheduleStatus status, LocalDateTime start) {
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(id);
        schedule.setActivity(activity);
        schedule.setStatus(status);
        schedule.setStartDateTime(start);
        return schedule;
    }

    // ===== Editar =====

    @Test
    void updateChangesOnlyTheEditableFields() {
        Activity updated = service.update(EMAIL, ACTIVITY_ID, validDto());

        assertThat(updated.getDescription()).isEqualTo("Nueva descripción");
        assertThat(updated.getPricePerPerson()).isEqualByComparingTo("750");
        assertThat(updated.getDurationMinutes()).isEqualTo(90);
        assertThat(updated.getMinAge()).isEqualTo(12);
        assertThat(updated.getRequirements()).isNull();
        assertThat(updated.getEquipment()).isEqualTo("Botas");
        assertThat(updated.getBookingCutoffMinutes()).isEqualTo(60);
        assertThat(updated.getCancellationCutoffMinutes()).isEqualTo(1440);
        assertThat(updated.getRefundPercentage()).isEqualByComparingTo("50");
        assertThat(updated.getActivityTypes()).containsExactly(hiking);
        // Nombre y lugar no se pueden editar
        assertThat(updated.getName()).isEqualTo("Trekking");
        assertThat(updated.getAddress()).isEqualTo("Plaza");
        assertThat(updated.getLocality()).isEqualTo("Minas");
    }

    @Test
    void updateClearsTheCancellationPolicyWhenCancellationsAreNoLongerAllowed() {
        activity.setCancellationAllowed(true);
        activity.setCancellationCutoffMinutes(1440);
        activity.setRefundPercentage(new BigDecimal("50"));
        ActivityDTO dto = validDto();
        dto.setCancellationAllowed(false);

        Activity updated = service.update(EMAIL, ACTIVITY_ID, dto);

        assertThat(updated.isCancellationAllowed()).isFalse();
        assertThat(updated.getCancellationCutoffMinutes()).isNull();
        assertThat(updated.getRefundPercentage()).isNull();
    }

    @Test
    void updateRejectsInvalidPriceWithoutSaving() {
        ActivityDTO dto = validDto();
        dto.setPricePerPerson(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.update(EMAIL, ACTIVITY_ID, dto))
                .isInstanceOf(ActivityValidationException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void updateRejectsADisabledType() {
        hiking.setEnabled(false);

        assertThatThrownBy(() -> service.update(EMAIL, ACTIVITY_ID, validDto()))
                .isInstanceOf(ActivityValidationException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void updateOfAnotherProvidersActivityFails() {
        when(activityRepository.findByIdAndBusinessProviderId(99L, PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EMAIL, 99L, validDto()))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    // ===== Suspender toda la actividad =====

    @Test
    void suspendingTheActivityMarksItSuspendedAndSuspendsOnlyItsFutureSchedules() {
        ActivitySchedule future1 = schedule(1L, ScheduleStatus.SCHEDULED, LocalDateTime.now().plusDays(1));
        ActivitySchedule future2 = schedule(2L, ScheduleStatus.SCHEDULED, LocalDateTime.now().plusDays(2));
        ActivitySchedule alreadySuspended = schedule(3L, ScheduleStatus.SUSPENDED, LocalDateTime.now().plusDays(3));
        ActivitySchedule past = schedule(4L, ScheduleStatus.SCHEDULED, LocalDateTime.now().minusDays(1));
        when(activityRepository.findSchedulesOf(ACTIVITY_ID))
                .thenReturn(List.of(past, future1, alreadySuspended, future2));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(future1));
        when(scheduleRepository.findById(2L)).thenReturn(Optional.of(future2));
        when(cancellationService.cancelForSuspension(1L)).thenReturn(new SuspensionResult(2, 2, 0));
        when(cancellationService.cancelForSuspension(2L)).thenReturn(new SuspensionResult(1, 0, 1));

        SuspensionResult result = service.suspendActivity(EMAIL, ACTIVITY_ID);

        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.SUSPENDED);
        assertThat(future1.getStatus()).isEqualTo(ScheduleStatus.SUSPENDED);
        assertThat(future2.getStatus()).isEqualTo(ScheduleStatus.SUSPENDED);
        assertThat(past.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        // Se reutiliza la cancelación con reembolso del 100 %, solo para los horarios futuros
        verify(cancellationService).cancelForSuspension(1L);
        verify(cancellationService).cancelForSuspension(2L);
        verify(cancellationService, never()).cancelForSuspension(3L);
        verify(cancellationService, never()).cancelForSuspension(4L);
        assertThat(result).isEqualTo(new SuspensionResult(3, 2, 1));
    }

    @Test
    void suspendingAnActivityWithoutSchedulesJustMarksItSuspended() {
        when(activityRepository.findSchedulesOf(ACTIVITY_ID)).thenReturn(List.of());

        SuspensionResult result = service.suspendActivity(EMAIL, ACTIVITY_ID);

        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.SUSPENDED);
        assertThat(result).isEqualTo(new SuspensionResult(0, 0, 0));
        verify(cancellationService, never()).cancelForSuspension(anyLong());
    }

    @Test
    void suspendingAnotherProvidersActivityFails() {
        when(activityRepository.findByIdAndBusinessProviderId(99L, PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendActivity(EMAIL, 99L)).isInstanceOf(ActivityNotFoundException.class);
        verify(cancellationService, never()).cancelForSuspension(anyLong());
    }
}
