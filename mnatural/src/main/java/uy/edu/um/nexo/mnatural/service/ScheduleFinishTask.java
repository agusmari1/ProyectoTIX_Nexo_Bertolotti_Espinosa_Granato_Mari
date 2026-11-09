package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;

import java.time.LocalDateTime;
import java.util.List;

// Cada 5 minutos pasa a FINISHED los horarios programados que ya terminaron
// (hora de inicio más la duración de la actividad). Los suspendidos no se tocan
@Component
public class ScheduleFinishTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduleFinishTask.class);

    private final ActivityScheduleRepository scheduleRepository;

    public ScheduleFinishTask(ActivityScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    @Transactional
    public int finishEndedSchedules() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ended = scheduleRepository.findStartedScheduled(now).stream()
                .filter(schedule -> !now.isBefore(ReviewService.endOf(schedule)))
                .map(ActivitySchedule::getId)
                .toList();
        if (ended.isEmpty()) {
            return 0;
        }
        int finished = scheduleRepository.markFinished(ended);
        log.info("{} horarios terminaron y pasaron a FINISHED", finished);
        return finished;
    }
}
