package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uy.edu.um.nexo.mnatural.dto.ActivityFilterDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.repository.TestData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private List<Activity> keyword(String q) {
        return service.searchByKeyword(q);
    }

    private List<String> names(List<Activity> activities) {
        return activities.stream().map(Activity::getName).toList();
    }
}
