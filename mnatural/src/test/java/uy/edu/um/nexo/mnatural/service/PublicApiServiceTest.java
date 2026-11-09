package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uy.edu.um.nexo.mnatural.dto.ApiAvailableActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ApiBusinessStatusDTO;
import uy.edu.um.nexo.mnatural.dto.ApiTopActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.InvalidApiRequestException;
import uy.edu.um.nexo.mnatural.repository.ActivityCatalogRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.PublicApiRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: las reglas de la API sin base de datos.
// Las consultas reales se prueban en PublicApiIntegrationTest
class PublicApiServiceTest {

    private static final LocalDate FUTURE_DAY = LocalDate.now().plusDays(10);

    private PublicApiRepository apiRepository;
    private ActivityCatalogRepository catalogRepository;
    private BusinessRepository businessRepository;
    private BookingService bookingService;
    private ReviewService reviewService;
    private PublicApiService service;

    @BeforeEach
    void setUp() {
        apiRepository = mock(PublicApiRepository.class);
        catalogRepository = mock(ActivityCatalogRepository.class);
        businessRepository = mock(BusinessRepository.class);
        bookingService = mock(BookingService.class);
        reviewService = mock(ReviewService.class);
        service = new PublicApiService(apiRepository, catalogRepository, businessRepository, bookingService,
                reviewService);
    }

    @Test
    void businessStatusOfMissingBusinessSaysItDoesNotExist() {
        when(businessRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.businessStatus(1L)).isEqualTo(new ApiBusinessStatusDTO(false, false, null));
    }

    @Test
    void businessStatusOfApprovedProviderIncludesTheName() {
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business(ApprovalStatus.APPROVED)));

        assertThat(service.businessStatus(1L)).isEqualTo(new ApiBusinessStatusDTO(true, true, "Emprendimiento"));
    }

    @Test
    void businessStatusOfPendingOrRejectedProviderHidesTheName() {
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business(ApprovalStatus.PENDING)));
        when(businessRepository.findById(2L)).thenReturn(Optional.of(business(ApprovalStatus.REJECTED)));

        ApiBusinessStatusDTO expected = new ApiBusinessStatusDTO(true, false, null);
        assertThat(service.businessStatus(1L)).isEqualTo(expected);
        assertThat(service.businessStatus(2L)).isEqualTo(expected);
    }

    @Test
    void availableRequiresDepartmentOrLocality() {
        assertThatThrownBy(() -> service.findAvailable(FUTURE_DAY, null, "   "))
                .isInstanceOf(InvalidApiRequestException.class);
    }

    @Test
    void availableGroupsSchedulesByActivityWithTheirFreeSpots() {
        Activity activity = activity(10L, 0);
        ActivitySchedule morning = schedule(100L, activity, FUTURE_DAY.atTime(9, 0));
        ActivitySchedule afternoon = schedule(101L, activity, FUTURE_DAY.atTime(15, 0));
        givenSchedules(morning, afternoon);
        when(bookingService.availableSpots(100L)).thenReturn(4);
        when(bookingService.availableSpots(101L)).thenReturn(2);

        List<ApiAvailableActivityDTO> result = service.findAvailable(FUTURE_DAY, Department.ROCHA, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).activity().id()).isEqualTo(10L);
        assertThat(result.get(0).schedules()).extracting("availableSpots").containsExactly(4, 2);
    }

    @Test
    void availableSkipsSchedulesWithoutFreeSpots() {
        givenSchedules(schedule(100L, activity(10L, 0), FUTURE_DAY.atTime(9, 0)));
        when(bookingService.availableSpots(100L)).thenReturn(0);

        assertThat(service.findAvailable(FUTURE_DAY, Department.ROCHA, null)).isEmpty();
    }

    @Test
    void availableSkipsSchedulesPastTheBookingDeadline() {
        // Empieza en 30 minutos, pero se puede reservar solo hasta 60 minutos antes
        Activity activity = activity(10L, 60);
        givenSchedules(schedule(100L, activity, LocalDateTime.now().plusMinutes(30)));

        assertThat(service.findAvailable(LocalDate.now(), Department.ROCHA, null)).isEmpty();
        verify(bookingService, never()).availableSpots(100L);
    }

    @Test
    void topIsEmptyWhenThereAreNoReviewsThisMonth() {
        when(reviewService.findTopRated(Department.ROCHA, YearMonth.now())).thenReturn(List.of());

        assertThat(service.findTopOfMonth(Department.ROCHA)).isEmpty();
    }

    @Test
    void topKeepsTheOrderOfTheRankingWithAverageAndCount() {
        when(reviewService.findTopRated(Department.ROCHA, YearMonth.now())).thenReturn(List.of(
                new ReviewSummaryDTO(11L, 5.0, 1L), new ReviewSummaryDTO(10L, 4.5, 2L)));
        when(catalogRepository.findByIdAndStatus(11L, ActivityStatus.PUBLISHED)).thenReturn(Optional.of(activity(11L, 0)));
        when(catalogRepository.findByIdAndStatus(10L, ActivityStatus.PUBLISHED)).thenReturn(Optional.of(activity(10L, 0)));

        List<ApiTopActivityDTO> top = service.findTopOfMonth(Department.ROCHA);

        assertThat(top).extracting(t -> t.activity().id()).containsExactly(11L, 10L);
        assertThat(top.get(1).averageRating()).isEqualTo(4.5);
        assertThat(top.get(1).ratingCount()).isEqualTo(2L);
    }

    @Test
    void searchRejectsBlankOrTooLongKeywords() {
        assertThatThrownBy(() -> service.search("  ")).isInstanceOf(InvalidApiRequestException.class);
        assertThatThrownBy(() -> service.search("a".repeat(101))).isInstanceOf(InvalidApiRequestException.class);
    }

    @SuppressWarnings("unchecked")
    private void givenSchedules(ActivitySchedule... schedules) {
        when(apiRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(schedules));
    }

    private Business business(ApprovalStatus status) {
        Provider provider = new Provider();
        provider.setApprovalStatus(status);
        Business business = new Business();
        business.setName("Emprendimiento");
        business.setProvider(provider);
        return business;
    }

    private Activity activity(Long id, int bookingCutoffMinutes) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setName("Actividad");
        activity.setBookingCutoffMinutes(bookingCutoffMinutes);
        activity.setBusiness(business(ApprovalStatus.APPROVED));
        return activity;
    }

    private ActivitySchedule schedule(Long id, Activity activity, LocalDateTime start) {
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(id);
        schedule.setActivity(activity);
        schedule.setStartDateTime(start);
        return schedule;
    }
}
