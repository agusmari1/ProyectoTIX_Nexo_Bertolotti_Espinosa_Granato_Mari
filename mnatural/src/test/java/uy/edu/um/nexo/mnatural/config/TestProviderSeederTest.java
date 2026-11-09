package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Se prueba con un contexto mínimo en memoria y repositorios simulados: no se toca la base de datos
class TestProviderSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);
    private final BusinessRepository businessRepository = mock(BusinessRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private ApplicationContextRunner runner() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        return new ApplicationContextRunner()
                .withUserConfiguration(TestProviderSeeder.class)
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(ProviderRepository.class, () -> providerRepository)
                .withBean(BusinessRepository.class, () -> businessRepository)
                .withBean(PasswordEncoder.class, () -> passwordEncoder);
    }

    @Test
    void itIsOffByDefault() {
        runner().run(context -> assertThat(context).doesNotHaveBean(TestProviderSeeder.class));
    }

    @Test
    void itStaysOffWhenThePropertyIsFalseOrAnythingElse() {
        runner().withPropertyValues("app.seed.test-providers=false")
                .run(context -> assertThat(context).doesNotHaveBean(TestProviderSeeder.class));
        runner().withPropertyValues("app.seed.test-providers=si")
                .run(context -> assertThat(context).doesNotHaveBean(TestProviderSeeder.class));
    }

    @Test
    void itRunsOnlyWhenExplicitlyEnabled() {
        runner().withPropertyValues("app.seed.test-providers=true")
                .run(context -> assertThat(context).hasSingleBean(TestProviderSeeder.class));
    }

    @Test
    void whenEnabledItCreatesTheThreeTestProvidersWithTheirStatuses() {
        runner().withPropertyValues("app.seed.test-providers=true").run(context -> {
            context.getBean(TestProviderSeeder.class).run();

            ArgumentCaptor<Provider> captor = ArgumentCaptor.forClass(Provider.class);
            verify(providerRepository, times(3)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(Provider::getApprovalStatus)
                    .containsExactly(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED, ApprovalStatus.PENDING);
            verify(businessRepository, times(3)).save(any());
        });
    }

    @Test
    void whenEnabledItDoesNotDuplicateProvidersThatAlreadyExist() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        runner().withPropertyValues("app.seed.test-providers=true").run(context -> {
            context.getBean(TestProviderSeeder.class).run();

            verify(providerRepository, never()).save(any());
            verify(businessRepository, never()).save(any());
        });
    }
}
