package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba las rutas contra la base real y con la seguridad activa, sin iniciar sesión.
// @Transactional deshace al final todo lo que crea cada test
@SpringBootTest
@Transactional
class PublicApiIntegrationTest {

    // Lejos en el futuro para que los horarios sigan disponibles
    private static final LocalDate DAY = LocalDate.of(2099, 3, 15);
    private static final String PUBLISHED = "Zzapi Kayak en Piriápolis";
    private static final String APPROVED_BUSINESS = "Zzapi Emprendimiento Aprobado";
    private static final String PENDING_BUSINESS = "Zzapi Emprendimiento Pendiente";
    private static final String PRIVATE_EMAIL = "zzapi-aprobado@test.local";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ActivityTypeRepository activityTypeRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ActivityScheduleRepository scheduleRepository;

    private MockMvc mockMvc;
    private Business approvedBusiness;
    private Business pendingBusiness;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();

        approvedBusiness = business(APPROVED_BUSINESS, PRIVATE_EMAIL, ApprovalStatus.APPROVED);
        pendingBusiness = business(PENDING_BUSINESS, "zzapi-pendiente@test.local", ApprovalStatus.PENDING);

        ActivityType type = new ActivityType();
        type.setName("Zzapi Remo Único");
        activityTypeRepository.save(type);

        Activity published = activity(PUBLISHED, approvedBusiness, ActivityStatus.PUBLISHED, type);
        schedule(published, 10, 5, ScheduleStatus.SCHEDULED);
        schedule(published, 15, 0, ScheduleStatus.SCHEDULED);
        schedule(published, 18, 5, ScheduleStatus.SUSPENDED);

        schedule(activity("Zzapi Borrador", approvedBusiness, ActivityStatus.DRAFT, type), 10, 5, ScheduleStatus.SCHEDULED);
        schedule(activity("Zzapi De Pendiente", pendingBusiness, ActivityStatus.PUBLISHED, type), 10, 5, ScheduleStatus.SCHEDULED);
    }

    @Test
    void businessesShowsOnlyApprovedProvidersAndNoPrivateData() throws Exception {
        String body = mockMvc.perform(get("/api/businesses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[*].name", hasItem(APPROVED_BUSINESS)))
                .andExpect(jsonPath("$[*].name", not(hasItem(PENDING_BUSINESS))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(PRIVATE_EMAIL, "099111222", "Calle Privada 1", "passwordHash");
    }

    @Test
    void statusOfApprovedBusiness() throws Exception {
        mockMvc.perform(get("/api/businesses/{id}/status", approvedBusiness.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.providerApproved").value(true))
                .andExpect(jsonPath("$.name").value(APPROVED_BUSINESS));
    }

    @Test
    void statusOfPendingBusinessHidesTheName() throws Exception {
        mockMvc.perform(get("/api/businesses/{id}/status", pendingBusiness.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.providerApproved").value(false))
                .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void statusOfMissingBusiness() throws Exception {
        mockMvc.perform(get("/api/businesses/{id}/status", Long.MAX_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.providerApproved").value(false));
    }

    @Test
    void statusWithInvalidIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/businesses/abc/status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void availableShowsOnlyBookableSchedulesOfPublicActivities() throws Exception {
        mockMvc.perform(get("/api/activities/available")
                        .param("date", DAY.toString())
                        .param("department", "MALDONADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].activity.name").value(PUBLISHED))
                .andExpect(jsonPath("$[0].activity.businessName").value(APPROVED_BUSINESS))
                // El de las 15 no tiene lugares y el de las 18 está suspendido
                .andExpect(jsonPath("$[0].schedules.length()").value(1))
                .andExpect(jsonPath("$[0].schedules[0].startDateTime").value("2099-03-15T10:00:00"))
                .andExpect(jsonPath("$[0].schedules[0].availableSpots").value(5));
    }

    @Test
    void availableFindsLocalityWithoutAccents() throws Exception {
        mockMvc.perform(get("/api/activities/available")
                        .param("date", DAY.toString())
                        .param("locality", "PIRIAPOLIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activity.name").value(PUBLISHED));
    }

    @Test
    void availableIsEmptyOnAnotherDay() throws Exception {
        mockMvc.perform(get("/api/activities/available")
                        .param("date", DAY.plusDays(1).toString())
                        .param("department", "MALDONADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void availableRejectsMissingOrInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/activities/available").param("department", "MALDONADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el parámetro obligatorio 'date'."));
        mockMvc.perform(get("/api/activities/available").param("date", DAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        mockMvc.perform(get("/api/activities/available").param("date", "15/03/2099").param("department", "MALDONADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El valor de 'date' no es válido."));
        mockMvc.perform(get("/api/activities/available").param("date", DAY.toString()).param("department", "ATLANTIDA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El valor de 'department' no es válido."));
    }

    @Test
    void topIsEmptyUntilReviewsExist() throws Exception {
        mockMvc.perform(get("/api/activities/top").param("department", "MALDONADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void topRequiresADepartment() throws Exception {
        mockMvc.perform(get("/api/activities/top"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el parámetro obligatorio 'department'."));
    }

    @Test
    void searchShowsOnlyPublicActivities() throws Exception {
        // El tipo lo comparten las tres actividades: solo aparece la publicada de un prestador aprobado
        mockMvc.perform(get("/api/activities/search").param("q", "ZZAPI REMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(PUBLISHED)));
    }

    @Test
    void searchIgnoresCaseAndAccents() throws Exception {
        mockMvc.perform(get("/api/activities/search").param("q", "zzapi KAYAK en piriapolis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(PUBLISHED)));
    }

    @Test
    void searchTreatsWildcardsAsText() throws Exception {
        mockMvc.perform(get("/api/activities/search").param("q", "zzapi%kayak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchRequiresAKeyword() throws Exception {
        mockMvc.perform(get("/api/activities/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el parámetro obligatorio 'q'."));
        mockMvc.perform(get("/api/activities/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        mockMvc.perform(post("/api/businesses")).andExpect(status().isForbidden());
    }

    private Business business(String name, String email, ApprovalStatus status) {
        Provider provider = new Provider();
        provider.setEmail(email);
        provider.setPasswordHash("hash");
        provider.setName("Zzapi");
        provider.setLastName("Prueba");
        provider.setDateOfBirth(LocalDate.of(1990, 1, 1));
        provider.setDocumentType(DocumentType.OTHER);
        provider.setDocumentNumber("00000000");
        provider.setCountry("Uruguay");
        provider.setApprovalStatus(status);
        providerRepository.save(provider);

        Business business = new Business();
        business.setName(name);
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle Privada 1");
        business.setPhoneNumber("099111222");
        business.setEmail(email);
        business.setProvider(provider);
        return businessRepository.save(business);
    }

    private Activity activity(String name, Business business, ActivityStatus status, ActivityType type) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setDescription("Descripción de prueba");
        activity.setAddress("Punto de encuentro");
        activity.setDepartment(Department.MALDONADO);
        activity.setLocality("Piriápolis");
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal("1000.00"));
        activity.setStatus(status);
        activity.setBusiness(business);
        activity.setActivityTypes(Set.of(type));
        return activityRepository.save(activity);
    }

    private void schedule(Activity activity, int hour, int capacity, ScheduleStatus status) {
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(DAY.atTime(hour, 0));
        schedule.setCapacity(capacity);
        schedule.setStatus(status);
        scheduleRepository.save(schedule);
    }
}
