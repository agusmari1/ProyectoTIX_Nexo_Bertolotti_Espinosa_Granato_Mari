package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uy.edu.um.nexo.mnatural.dto.ActivityFilterDTO;
import uy.edu.um.nexo.mnatural.dto.ScheduleAvailabilityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.repository.TestData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Filtros del portal público contra una base H2 en memoria (nunca la real): así se prueba la consulta
// que se arma de verdad. BookingService es un mock porque acá no importan los lugares libres
@DataJpaTest
@Import(ActivityCatalogService.class)
class ActivityCatalogServiceTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ActivityCatalogService service;
    @MockitoBean
    private BookingService bookingService;

    private TestData data;
    private Business business;
    private Activity kayak;
    private Activity horses;
    private Activity draft;

    @BeforeEach
    void setUp() {
        data = new TestData(em);
        business = data.business(data.provider(ApprovalStatus.APPROVED), "Aventuras del Este");
        Business other = data.business(data.provider(ApprovalStatus.APPROVED), "Estancia La Tranquera");
        kayak = data.activity(business, "Kayak al atardecer", Department.MALDONADO, "Piriápolis", "1500",
                ActivityStatus.PUBLISHED);
        horses = data.activity(other, "Cabalgata por la sierra", Department.LAVALLEJA, "Minas", "2500",
                ActivityStatus.PUBLISHED);
        horses.setDescription("Recorrido a caballo con almuerzo campero");
        draft = data.activity(business, "Kayak nocturno", Department.MALDONADO, "Piriápolis", "1800",
                ActivityStatus.DRAFT);
        em.flush();
    }

    // ===== Palabra clave =====

    @Test
    void keywordIgnoresCaseAndAccents() {
        assertThat(names(keyword("PIRIAPOLIS"))).containsExactly("Kayak al atardecer");
        assertThat(names(keyword("kayák"))).containsExactly("Kayak al atardecer");
    }

    @Test
    void keywordSearchesNameDescriptionLocalityAndBusinessName() {
        assertThat(names(keyword("atardecer"))).containsExactly("Kayak al atardecer");
        assertThat(names(keyword("campero"))).containsExactly("Cabalgata por la sierra");
        assertThat(names(keyword("minas"))).containsExactly("Cabalgata por la sierra");
        assertThat(names(keyword("tranquera"))).containsExactly("Cabalgata por la sierra");
    }

    @Test
    void keywordNeverReturnsUnpublishedActivities() {
        assertThat(names(keyword("nocturno"))).isEmpty();
    }

    @Test
    void keywordIsCombinedWithTheOtherFilters() {
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setQ("a");
        filter.setDepartment(Department.LAVALLEJA);

        assertThat(names(service.search(filter))).containsExactly("Cabalgata por la sierra");
    }

    @Test
    void wildcardsAreSearchedAsText() {
        assertThat(keyword("%")).isEmpty();
        assertThat(keyword("_")).isEmpty();
    }

    @Test
    void aBlankKeywordIsIgnored() {
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setQ("   ");

        assertThat(names(service.search(filter))).containsExactly("Cabalgata por la sierra", "Kayak al atardecer");
        assertThat(filter.isEmpty()).isTrue();
    }

    // ===== Otros filtros =====

    @Test
    void withoutFiltersShowsOnlyPublishedActivitiesByName() {
        assertThat(names(service.search(new ActivityFilterDTO()))).containsExactly("Cabalgata por la sierra", "Kayak al atardecer");
    }

    @Test
    void filtersByDepartmentAndLocality() {
        ActivityFilterDTO byDepartment = new ActivityFilterDTO();
        byDepartment.setDepartment(Department.LAVALLEJA);
        ActivityFilterDTO byLocality = new ActivityFilterDTO();
        byLocality.setLocality("piriá");

        assertThat(names(service.search(byDepartment))).containsExactly("Cabalgata por la sierra");
        assertThat(names(service.search(byLocality))).containsExactly("Kayak al atardecer");
    }

    @Test
    void filtersByTypeWithoutRepeatingActivitiesWithSeveralTypes() {
        ActivityType water = data.type("Acuática");
        ActivityType sunset = data.type("Atardecer");
        Activity both = data.activity(business, "Paddle al atardecer", Department.ROCHA, "La Paloma", "900",
                ActivityStatus.PUBLISHED, water, sunset);
        em.flush();
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setActivityTypeId(water.getId());

        assertThat(service.search(filter)).containsExactly(both);
    }

    @Test
    void filtersByPriceRangeEvenIfWrittenBackwards() {
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setMinPrice(new BigDecimal("2000"));
        filter.setMaxPrice(new BigDecimal("1000"));

        assertThat(names(service.search(filter))).containsExactly("Kayak al atardecer");
    }

    @Test
    void filtersByDateWithSchedulesThatAreScheduledAndHaveNotStarted() {
        LocalDate day = LocalDate.now().plusDays(7);
        data.schedule(kayak, day.atTime(18, 0), ScheduleStatus.SCHEDULED);
        // Un horario suspendido ese día no cuenta
        data.schedule(horses, day.atTime(9, 0), ScheduleStatus.SUSPENDED);
        // Un horario de otro día tampoco
        data.schedule(horses, day.plusDays(1).atTime(9, 0), ScheduleStatus.SCHEDULED);
        em.flush();
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setDate(day);

        assertThat(names(service.search(filter))).containsExactly("Kayak al atardecer");
    }

    // ===== Próximas fechas del detalle =====

    @Test
    void upcomingSchedulesAreFutureScheduledInOrderWithSpotsAndBookingDeadline() {
        kayak.setBookingCutoffMinutes(120);
        ActivitySchedule soon = data.schedule(kayak, LocalDateTime.now().plusHours(1), ScheduleStatus.SCHEDULED);
        ActivitySchedule later = data.schedule(kayak, LocalDateTime.now().plusDays(3), ScheduleStatus.SCHEDULED);
        data.schedule(kayak, LocalDateTime.now().plusDays(2), ScheduleStatus.SUSPENDED);
        data.schedule(kayak, LocalDateTime.now().minusDays(1), ScheduleStatus.FINISHED);
        em.flush();
        when(bookingService.availableSpots(soon.getId())).thenReturn(4);
        when(bookingService.availableSpots(later.getId())).thenReturn(0);

        List<ScheduleAvailabilityDTO> upcoming = service.upcomingSchedules(kayak);

        assertThat(upcoming).extracting(s -> s.getSchedule().getId()).containsExactly(soon.getId(), later.getId());
        // Empieza en 1 hora pero se reserva hasta 2 horas antes: ya cerró
        assertThat(upcoming.get(0).isBookingOpen()).isFalse();
        assertThat(upcoming.get(0).getAvailableSpots()).isEqualTo(4);
        // Abierta pero sin lugares: no se puede reservar
        assertThat(upcoming.get(1).isBookingOpen()).isTrue();
        assertThat(upcoming.get(1).isBookable()).isFalse();
    }

    @Test
    void theDetailIsOnlyForPublishedActivities() {
        assertThat(service.findPublished(kayak.getId())).contains(kayak);
        assertThat(service.findPublished(draft.getId())).isEmpty();
    }

    private List<Activity> keyword(String q) {
        return service.searchByKeyword(q);
    }

    private List<String> names(List<Activity> activities) {
        return activities.stream().map(Activity::getName).toList();
    }
}
