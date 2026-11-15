package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ActivityFilterDTO;
import uy.edu.um.nexo.mnatural.dto.ApiActivityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// El buscador por palabra clave del portal contra la base real. Todo lo que crea se deshace al final de cada test
// (@Transactional). Las actividades llevan "Zzkw" para no mezclarse con las que ya haya en la base
@SpringBootTest
@Transactional
class KeywordSearchIntegrationTest {

    @Autowired
    private ActivityCatalogService catalogService;
    @Autowired
    private PublicApiService apiService;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ActivityTypeRepository activityTypeRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private Business business;
    private ActivityType adventure;
    private ActivityType gastronomy;

    @BeforeEach
    void setUp() {
        Provider provider = new Provider();
        provider.setEmail("zzkw@test.local");
        provider.setPasswordHash("hash");
        provider.setName("Zzkw");
        provider.setLastName("Prueba");
        provider.setDateOfBirth(LocalDate.of(1990, 1, 1));
        provider.setDocumentType(DocumentType.OTHER);
        provider.setDocumentNumber("00000000");
        provider.setCountry("Uruguay");
        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        providerRepository.save(provider);

        business = new Business();
        business.setName("Zzkw Emprendimiento");
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle 1");
        business.setPhoneNumber("099111222");
        business.setEmail("zzkw@test.local");
        business.setProvider(provider);
        businessRepository.save(business);

        adventure = type("Zzkw Aventura");
        gastronomy = type("Zzkw Gastronomía");

        activity("Zzkw Cabalgata Serrana", "Un paseo tranquilo", "Minas", Department.LAVALLEJA,
                ActivityStatus.PUBLISHED, adventure);
        activity("Zzkw Kayak", "Remo por la ría con vista al atardecer", "Piriápolis", Department.MALDONADO,
                ActivityStatus.PUBLISHED, adventure);
        activity("Zzkw Tour del Sabor", "Degustación de vinos", "Colonia", Department.COLONIA,
                ActivityStatus.PUBLISHED, gastronomy);
        activity("Zzkw Borrador Cabalgata", "Todavía no se publica", "Minas", Department.LAVALLEJA,
                ActivityStatus.DRAFT, adventure);
    }

    // Solo las actividades de este test: en la base puede haber otras (por ejemplo las de demostración)
    private List<String> portal(String keyword) {
        return portalAll(keyword).stream().filter(name -> name.startsWith("Zzkw")).toList();
    }

    private List<String> portalAll(String keyword) {
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setQ(keyword);
        return catalogService.search(filter).stream().map(Activity::getName).sorted().toList();
    }

    private List<String> apiAll(String keyword) {
        return apiService.search(keyword).stream().map(ApiActivityDTO::name).sorted().toList();
    }

    @Test
    void findsByNameWithoutCaringAboutCase() {
        assertThat(portal("ZZKW CABALGATA")).containsExactly("Zzkw Cabalgata Serrana");
        assertThat(portal("zzkw kayak")).containsExactly("Zzkw Kayak");
    }

    @Test
    void draftActivitiesNeverAppear() {
        assertThat(portal("borrador")).isEmpty();
        assertThat(portal("zzkw")).doesNotContain("Zzkw Borrador Cabalgata").hasSize(3);
    }

    @Test
    void ignoresAccentsInBothDirections() {
        assertThat(portal("piriapolis")).containsExactly("Zzkw Kayak");
        assertThat(portal("PIRIÁPOLIS")).containsExactly("Zzkw Kayak");
        assertThat(portal("zzkw gastronomia")).containsExactly("Zzkw Tour del Sabor");
        assertThat(portal("degustacion")).containsExactly("Zzkw Tour del Sabor");
    }

    @Test
    void findsByDescriptionLocalityAndType() {
        assertThat(portal("atardecer")).containsExactly("Zzkw Kayak");
        assertThat(portal("colonia")).contains("Zzkw Tour del Sabor");
        assertThat(portal("zzkw aventura")).containsExactly("Zzkw Cabalgata Serrana", "Zzkw Kayak");
    }

    @Test
    void anActivityWithSeveralMatchesAppearsOnlyOnce() {
        // "zzkw" está en el nombre, y en el tipo de las tres: no se repite ninguna
        assertThat(portal("zzkw")).containsExactly("Zzkw Cabalgata Serrana", "Zzkw Kayak", "Zzkw Tour del Sabor");
    }

    @Test
    void wildcardsAreSearchedAsPlainText() {
        assertThat(portal("zzkw%")).isEmpty();
        assertThat(portal("zzkw_kayak")).isEmpty();
        assertThat(portal("%")).isEmpty();
    }

    @Test
    void aBlankKeywordIsIgnoredAndTheRestOfTheFiltersStillApply() {
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setQ("   ");
        assertThat(filter.isEmpty()).isTrue();
        assertThat(catalogService.search(filter)).extracting(Activity::getName).contains("Zzkw Kayak");

        filter.setDepartment(Department.COLONIA);
        assertThat(filter.isEmpty()).isFalse();
        assertThat(catalogService.search(filter)).extracting(Activity::getName)
                .contains("Zzkw Tour del Sabor").doesNotContain("Zzkw Kayak");
    }

    @Test
    void theKeywordCombinesWithTheOtherFilters() {
        ActivityFilterDTO filter = new ActivityFilterDTO();
        filter.setQ("zzkw");
        filter.setDepartment(Department.MALDONADO);
        assertThat(catalogService.search(filter)).extracting(Activity::getName).containsExactly("Zzkw Kayak");

        filter.setActivityTypeId(gastronomy.getId());
        assertThat(catalogService.search(filter)).isEmpty();

        filter.setDepartment(null);
        assertThat(catalogService.search(filter)).extracting(Activity::getName).containsExactly("Zzkw Tour del Sabor");

        filter.setMinPrice(new BigDecimal("5000"));
        assertThat(catalogService.search(filter)).isEmpty();
    }

    @Test
    void aVeryLongKeywordDoesNotBreakThePortal() {
        assertThat(portal("a".repeat(500))).isEmpty();
    }

    @Test
    void thePortalAndTheApiSearchWithExactlyTheSameCriteria() {
        for (String keyword : new String[]{"zzkw", "ZZKW CABALGATA", "piriapolis", "PIRIÁPOLIS", "atardecer",
                "gastronomia", "zzkw aventura", "degustacion", "zzkw%", "zzkw_kayak", "borrador", "nada que ver"}) {
            // Todos los resultados, no solo los de este test: tiene que coincidir también con lo que ya haya en la base
            assertThat(portalAll(keyword)).as("palabra: " + keyword).isEqualTo(apiAll(keyword));
        }
    }

    private ActivityType type(String name) {
        ActivityType type = new ActivityType();
        type.setName(name);
        return activityTypeRepository.save(type);
    }

    private void activity(String name, String description, String locality, Department department,
                          ActivityStatus status, ActivityType type) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setDescription(description);
        activity.setAddress("Punto de encuentro");
        activity.setDepartment(department);
        activity.setLocality(locality);
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal("1000.00"));
        activity.setStatus(status);
        activity.setBusiness(business);
        activity.setActivityTypes(Set.of(type));
        activityRepository.save(activity);
    }
}
