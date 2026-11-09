package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: el repositorio es un mock
class ScheduleFinishTaskTest {

    private final ActivityScheduleRepository repository = mock(ActivityScheduleRepository.class);
    private final ScheduleFinishTask task = new ScheduleFinishTask(repository);

    @Test
    void onlyTheSchedulesWhoseActivityAlreadyEndedAreFinished() {
        // Empezó hace 3 h y dura 2 h: terminó
        ActivitySchedule ended = schedule(1L, LocalDateTime.now().minusHours(3), 120);
        // Empezó hace 1 h y dura 2 h: sigue en curso
        ActivitySchedule running = schedule(2L, LocalDateTime.now().minusHours(1), 120);
        when(repository.findStartedScheduled(any())).thenReturn(List.of(ended, running));
        when(repository.markFinished(List.of(1L))).thenReturn(1);

        assertThat(task.finishEndedSchedules()).isEqualTo(1);
        verify(repository).markFinished(List.of(1L));
    }

    @Test
    void ifNothingEndedNothingIsUpdated() {
        when(repository.findStartedScheduled(any()))
                .thenReturn(List.of(schedule(2L, LocalDateTime.now().minusMinutes(10), 60)));

        assertThat(task.finishEndedSchedules()).isZero();
        verify(repository, never()).markFinished(anyCollection());
    }

    private ActivitySchedule schedule(Long id, LocalDateTime start, int durationMinutes) {
        Activity activity = new Activity();
        activity.setDurationMinutes(durationMinutes);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(id);
        schedule.setActivity(activity);
        schedule.setStartDateTime(start);
        return schedule;
    }
}
