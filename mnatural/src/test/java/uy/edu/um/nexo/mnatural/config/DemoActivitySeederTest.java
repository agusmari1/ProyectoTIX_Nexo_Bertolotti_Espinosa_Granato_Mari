package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Repositorios simulados: no se toca la base de datos
class DemoActivitySeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BusinessRepository businessRepository = mock(BusinessRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final ActivityScheduleRepository scheduleRepository = mock(ActivityScheduleRepository.class);
    private final ActivityTypeRepository typeRepository = mock(ActivityTypeRepository.class);

    private DemoActivitySeeder seeder;
    private Provider provider;
    private Business business;

    @BeforeEach
    void setUp() {
        seeder = new DemoActivitySeeder(userRepository, businessRepository, activityRepository,
                scheduleRepository, typeRepository);
        provider = new Provider();
        provider.setId(1L);
        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        business = new Business();
        business.setProvider(provider);
        when(userRepository.findByEmail(DemoActivitySeeder.DEMO_PROVIDER_EMAIL)).thenReturn(Optional.of(provider));
        when(businessRepository.findByProviderId(1L)).thenReturn(List.of(business));
        when(activityRepository.findByBusinessProviderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(typeRepository.findAll()).thenReturn(List.of());
        when(typeRepository.save(any(ActivityType.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void itCreatesThreeActivitiesPerDepartmentPublishedWithFutureSchedules() {
        seeder.run();

        ArgumentCaptor<Activity> activities = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository, times(DemoActivitySeeder.DEMOS.size())).save(activities.capture());
        assertThat(activities.getAllValues()).hasSize(15);
        assertThat(activities.getAllValues().stream().map(Activity::getDepartment).distinct()).hasSize(5);
        assertThat(activities.getAllValues()).allSatisfy(a -> {
            assertThat(a.getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
            assertThat(a.getBusiness()).isSameAs(business);
            assertThat(a.getActivityTypes()).isNotEmpty();
            assertThat(a.getPricePerPerson().signum()).isPositive();
            assertThat(a.getDurationMinutes()).isPositive();
            // Si admite cancelaciones, trae plazo y porcentaje completos
            if (a.isCancellationAllowed()) {
                assertThat(a.getCancellationCutoffMinutes()).isNotNull();
                assertThat(a.getRefundPercentage()).isNotNull();
            }
        });

        ArgumentCaptor<ActivitySchedule> schedules = ArgumentCaptor.forClass(ActivitySchedule.class);
        verify(scheduleRepository, times(15 * DemoActivitySeeder.SCHEDULES.length)).save(schedules.capture());
        assertThat(schedules.getAllValues()).allSatisfy(s -> {
            assertThat(s.getStartDateTime()).isAfter(LocalDateTime.now());
            assertThat(s.getCapacity()).isIn(java.util.Arrays.stream(DemoActivitySeeder.CAPACITIES).boxed().toList());
        });
    }

    @Test
    void everyActivityHasItsOwnCapacityAndThereIsOneForEachDemoActivity() {
        assertThat(DemoActivitySeeder.CAPACITIES).hasSize(DemoActivitySeeder.DEMOS.size());
        assertThat(java.util.Arrays.stream(DemoActivitySeeder.CAPACITIES).distinct().count()).isGreaterThan(5);

        seeder.run();

        ArgumentCaptor<ActivitySchedule> schedules = ArgumentCaptor.forClass(ActivitySchedule.class);
        verify(scheduleRepository, times(15 * DemoActivitySeeder.SCHEDULES.length)).save(schedules.capture());
        // El primer horario de la primera actividad lleva la capacidad de la primera
        assertThat(schedules.getAllValues().getFirst().getCapacity()).isEqualTo(DemoActivitySeeder.CAPACITIES[0]);
    }

    @Test
    void everyDemoActivityHasItsOwnPhotoInTheProject() {
        for (DemoActivitySeeder.Demo demo : DemoActivitySeeder.DEMOS) {
            String file = "static/img/actividades/" + uy.edu.um.nexo.mnatural.service.ActivityPhotos.slug(demo.name()) + ".jpg";

            assertThat(new org.springframework.core.io.ClassPathResource(file).exists())
                    .as("falta la foto de \"" + demo.name() + "\": " + file).isTrue();
        }
    }

    @Test
    void theTypesThatAreMissingAreCreatedOnlyOnce() {
        seeder.run();

        // Hay 5 tipos distintos en los datos de demostración y cada uno se crea una sola vez
        verify(typeRepository, times(5)).save(any(ActivityType.class));
    }

    @Test
    void anExistingTypeIsReusedIgnoringCase() {
        ActivityType existing = new ActivityType();
        existing.setName("aventura");
        existing.setEnabled(true);
        when(typeRepository.findAll()).thenReturn(List.of(existing));

        seeder.run();

        verify(typeRepository, times(4)).save(any(ActivityType.class));
    }

    @Test
    void runningItTwiceDoesNotDuplicateActivities() {
        List<Activity> alreadyThere = DemoActivitySeeder.DEMOS.stream().map(demo -> {
            Activity activity = new Activity();
            activity.setName(demo.name());
            return activity;
        }).toList();
        when(activityRepository.findByBusinessProviderIdOrderByCreatedAtDesc(1L)).thenReturn(alreadyThere);

        seeder.run();

        verify(activityRepository, never()).save(any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void withoutAnApprovedProviderWithABusinessNothingIsCreated() {
        when(businessRepository.findByProviderId(1L)).thenReturn(List.of());

        seeder.run();

        verify(activityRepository, never()).save(any());
    }

    @Test
    void aProviderThatIsNotApprovedIsNotUsed() {
        provider.setApprovalStatus(ApprovalStatus.PENDING);

        seeder.run();

        verify(activityRepository, never()).save(any());
    }

    @Test
    void itIsOffByDefaultAndOnlyRunsWhenExplicitlyEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(DemoActivitySeeder.class)
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(BusinessRepository.class, () -> businessRepository)
                .withBean(ActivityRepository.class, () -> activityRepository)
                .withBean(ActivityScheduleRepository.class, () -> scheduleRepository)
                .withBean(ActivityTypeRepository.class, () -> typeRepository);

        runner.run(context -> assertThat(context).doesNotHaveBean(DemoActivitySeeder.class));
        runner.withPropertyValues("app.seed.demo-activities=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoActivitySeeder.class));
        runner.withPropertyValues("app.seed.demo-activities=true")
                .run(context -> assertThat(context).hasSingleBean(DemoActivitySeeder.class));
    }
}
