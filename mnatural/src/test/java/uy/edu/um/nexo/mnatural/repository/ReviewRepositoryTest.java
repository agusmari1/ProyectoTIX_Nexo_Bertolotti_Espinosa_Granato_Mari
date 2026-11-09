package uy.edu.um.nexo.mnatural.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Prueba las consultas de ReviewRepository y ActivityScheduleRepository sobre H2 en memoria:
// @DataJpaTest reemplaza la base real por una embebida y deshace todo al terminar cada test
@DataJpaTest
class ReviewRepositoryTest {

    private static final LocalDateTime FEBRUARY = LocalDateTime.of(2026, 2, 10, 12, 0);
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 2, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 3, 1, 0, 0);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ActivityScheduleRepository scheduleRepository;

    private TestData data;
    private Business business;

    @BeforeEach
    void setUp() {
        data = new TestData(em);
        business = data.business(data.provider(ApprovalStatus.APPROVED), "Emprendimiento");
    }

    @Test
    void topRatedOrdersByAverageThenByCountAndKeepsOnlyThree() {
        Activity five = activity("Cinco", Department.ROCHA);
        data.reviewOf(five, 5, FEBRUARY);
        Activity fourTwice = activity("Cuatro dos veces", Department.ROCHA);
        data.reviewOf(fourTwice, 4, FEBRUARY);
        data.reviewOf(fourTwice, 4, FEBRUARY);
        Activity fourOnce = activity("Cuatro una vez", Department.ROCHA);
        data.reviewOf(fourOnce, 4, FEBRUARY);
        Activity three = activity("Tres", Department.ROCHA);
        data.reviewOf(three, 3, FEBRUARY);

        List<ReviewSummaryDTO> top = reviewRepository.findTopRated(Department.ROCHA, FROM, TO, PageRequest.of(0, 3));

        assertThat(top).extracting(ReviewSummaryDTO::activityId)
                .containsExactly(five.getId(), fourTwice.getId(), fourOnce.getId());
        assertThat(top.get(1).average()).isEqualTo(4.0);
        assertThat(top.get(1).count()).isEqualTo(2L);
    }

    @Test
    void topRatedOnlyCountsReviewsOfThatMonthDepartmentAndPublishedApprovedActivities() {
        Activity inside = activity("Dentro", Department.ROCHA);
        data.reviewOf(inside, 2, FEBRUARY);
        // Una reseña de enero no cuenta para febrero
        data.reviewOf(activity("Enero", Department.ROCHA), 5, LocalDateTime.of(2026, 1, 31, 23, 59));
        data.reviewOf(activity("Otro departamento", Department.SALTO), 5, FEBRUARY);
        data.reviewOf(data.activity(business, "Suspendida", Department.ROCHA, "La Paloma", "100",
                ActivityStatus.SUSPENDED), 5, FEBRUARY);
        Business pending = data.business(data.provider(ApprovalStatus.PENDING), "Pendiente");
        data.reviewOf(data.activity(pending, "De pendiente", Department.ROCHA, "La Paloma", "100",
                ActivityStatus.PUBLISHED), 5, FEBRUARY);
        Review deleted = data.reviewOf(activity("Eliminada", Department.ROCHA), 5, FEBRUARY);
        deleted.setRating(null);
        deleted.setDeletedAt(FEBRUARY);
        em.flush();

        List<ReviewSummaryDTO> top = reviewRepository.findTopRated(Department.ROCHA, FROM, TO, PageRequest.of(0, 3));

        assertThat(top).extracting(ReviewSummaryDTO::activityId).containsExactly(inside.getId());
    }

    @Test
    void summariesAndVisibleReviewsLeaveOutDeletedOnes() {
        Activity activity = activity("Kayak", Department.ROCHA);
        data.reviewOf(activity, 5, FEBRUARY);
        data.reviewOf(activity, 3, FEBRUARY.plusDays(1));
        Review deleted = data.reviewOf(activity, 1, FEBRUARY.plusDays(2));
        deleted.setRating(null);
        deleted.setComment(null);
        deleted.setDeletedAt(FEBRUARY.plusDays(3));
        em.flush();

        Map<Long, ReviewSummaryDTO> summaries = reviewRepository.summarizeAllActivities().stream()
                .collect(Collectors.toMap(ReviewSummaryDTO::activityId, s -> s));
        List<Review> visible = reviewRepository.findVisibleByActivityId(activity.getId());

        assertThat(summaries.get(activity.getId()).average()).isEqualTo(4.0);
        assertThat(summaries.get(activity.getId()).count()).isEqualTo(2L);
        // De la más nueva a la más vieja
        assertThat(visible).extracting(Review::getRating).containsExactly(3, 5);
    }

    @Test
    void markFinishedDoesNotOverwriteASuspendedSchedule() {
        Activity activity = activity("Kayak", Department.ROCHA);
        ActivitySchedule scheduled = data.schedule(activity, LocalDateTime.now().minusHours(3), ScheduleStatus.SCHEDULED);
        ActivitySchedule suspended = data.schedule(activity, LocalDateTime.now().minusHours(4), ScheduleStatus.SUSPENDED);
        ActivitySchedule future = data.schedule(activity, LocalDateTime.now().plusDays(1), ScheduleStatus.SCHEDULED);
        em.flush();

        List<ActivitySchedule> started = scheduleRepository.findStartedScheduled(LocalDateTime.now());
        int changed = scheduleRepository.markFinished(List.of(scheduled.getId(), suspended.getId()));
        em.clear();

        assertThat(started).extracting(ActivitySchedule::getId).containsExactly(scheduled.getId());
        assertThat(changed).isEqualTo(1);
        assertThat(em.find(ActivitySchedule.class, scheduled.getId()).getStatus()).isEqualTo(ScheduleStatus.FINISHED);
        assertThat(em.find(ActivitySchedule.class, suspended.getId()).getStatus()).isEqualTo(ScheduleStatus.SUSPENDED);
        assertThat(em.find(ActivitySchedule.class, future.getId()).getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
    }

    private Activity activity(String name, Department department) {
        return data.activity(business, name, department, "La Paloma", "1000", ActivityStatus.PUBLISHED);
    }
}
