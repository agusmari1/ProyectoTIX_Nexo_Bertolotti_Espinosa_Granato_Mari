package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.InvalidRejectionReasonException;
import uy.edu.um.nexo.mnatural.exception.ProviderNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ProviderNotPendingException;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario de la administración de prestadores: repositorio y publicador de eventos son mocks,
// no se toca la base de datos
class AdminServiceTest {

    private final ProviderRepository providerRepository = mock(ProviderRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private AdminService service;
    private Admin admin;

    @BeforeEach
    void setUp() {
        service = new AdminService(providerRepository, eventPublisher);
        admin = new Admin();
        admin.setId(1L);
        // save devuelve lo mismo que recibe, como hace JPA con una entidad existente
        when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Provider provider(Long id, ApprovalStatus status) {
        Provider provider = new Provider();
        provider.setId(id);
        provider.setApprovalStatus(status);
        return provider;
    }

    private Provider pendingProvider(Long id) {
        Provider provider = provider(id, ApprovalStatus.PENDING);
        when(providerRepository.findById(id)).thenReturn(Optional.of(provider));
        return provider;
    }

    // ===== Aprobar =====

    @Test
    void approveMarksTheProviderApprovedAndRecordsWhoAndWhen() {
        Provider provider = pendingProvider(5L);
        LocalDateTime before = LocalDateTime.now();

        Provider result = service.approve(5L, admin);

        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getReviewedBy()).isSameAs(admin);
        assertThat(result.getReviewedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(result.getRejectionReason()).isNull();
        verify(providerRepository).save(provider);
    }

    @Test
    void approvePublishesTheReviewedEventSoTheProviderIsEmailedAfterCommit() {
        Provider provider = pendingProvider(5L);

        service.approve(5L, admin);

        var captor = forClass(ProviderReviewedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().provider()).isSameAs(provider);
        assertThat(captor.getValue().provider().getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    // ===== Rechazar =====

    @Test
    void rejectMarksTheProviderRejectedWithTheTrimmedReasonAndWhoAndWhen() {
        pendingProvider(5L);

        Provider result = service.reject(5L, admin, "  Falta documentación  ");

        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Falta documentación");
        assertThat(result.getReviewedBy()).isSameAs(admin);
        assertThat(result.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectPublishesTheReviewedEventWithTheReason() {
        pendingProvider(5L);

        service.reject(5L, admin, "Datos incompletos");

        var captor = forClass(ProviderReviewedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().provider().getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(captor.getValue().provider().getRejectionReason()).isEqualTo("Datos incompletos");
    }

    @Test
    void theRejectionReasonIsMandatory() {
        Provider provider = pendingProvider(5L);

        for (String reason : new String[]{null, "", "    ", "\n\t "}) {
            assertThatThrownBy(() -> service.reject(5L, admin, reason))
                    .isInstanceOf(InvalidRejectionReasonException.class)
                    .hasMessageContaining("motivo");
        }

        // Sin motivo no pasa nada: sigue pendiente, no se guarda y no se avisa
        assertThat(provider.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(provider.getReviewedBy()).isNull();
        verify(providerRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theRejectionReasonCannotBeLongerThanTheLimit() {
        Provider provider = pendingProvider(5L);
        String tooLong = "a".repeat(AdminService.MAX_REJECTION_REASON_LENGTH + 1);

        assertThatThrownBy(() -> service.reject(5L, admin, tooLong))
                .isInstanceOf(InvalidRejectionReasonException.class)
                .hasMessageContaining(String.valueOf(AdminService.MAX_REJECTION_REASON_LENGTH));
        assertThat(provider.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void aReasonExactlyAtTheLimitIsAccepted() {
        pendingProvider(5L);
        String exactly = "a".repeat(AdminService.MAX_REJECTION_REASON_LENGTH);

        assertThat(service.reject(5L, admin, exactly).getRejectionReason()).hasSize(AdminService.MAX_REJECTION_REASON_LENGTH);
    }

    // ===== Solicitudes que no se pueden resolver =====

    @Test
    void anUnknownProviderCannotBeApprovedNorRejected() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L, admin)).isInstanceOf(ProviderNotFoundException.class);
        assertThatThrownBy(() -> service.reject(99L, admin, "motivo")).isInstanceOf(ProviderNotFoundException.class);
        verify(providerRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aRequestThatWasAlreadyResolvedCannotBeResolvedAgain() {
        for (ApprovalStatus resolved : new ApprovalStatus[]{ApprovalStatus.APPROVED, ApprovalStatus.REJECTED}) {
            Provider provider = provider(7L, resolved);
            when(providerRepository.findById(7L)).thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> service.approve(7L, admin)).isInstanceOf(ProviderNotPendingException.class);
            assertThatThrownBy(() -> service.reject(7L, admin, "motivo")).isInstanceOf(ProviderNotPendingException.class);
            // El estado original no se toca
            assertThat(provider.getApprovalStatus()).isEqualTo(resolved);
            assertThat(provider.getReviewedBy()).isNull();
        }
        verify(providerRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ===== Listados =====

    @Test
    void pendingProvidersComeInTheOrderTheRepositoryReturnsThem() {
        Provider first = provider(1L, ApprovalStatus.PENDING);
        Provider second = provider(2L, ApprovalStatus.PENDING);
        Provider third = provider(3L, ApprovalStatus.PENDING);
        when(providerRepository.findByApprovalStatusOrderByRequestedAtAscIdAsc(ApprovalStatus.PENDING))
                .thenReturn(List.of(first, second, third));

        assertThat(service.findPendingProviders()).containsExactly(first, second, third);
    }

    // El orden de llegada lo hace la consulta del repositorio. Sin base de datos se comprueba con el
    // analizador de nombres de Spring Data que el método ordena por fecha de solicitud y, si empatan, por id
    @Test
    void theArrivalOrderQueryOrdersByRequestTimeAscendingThenById() {
        Sort sort = new PartTree("findByApprovalStatusOrderByRequestedAtAscIdAsc", Provider.class).getSort();

        assertThat(sort.stream().map(order -> order.getProperty() + " " + order.getDirection()).toList())
                .containsExactly("requestedAt ASC", "id ASC");
        // Y AdminService usa justamente ese método con el estado PENDING (lo prueba el test anterior)
    }

    @Test
    void approvedAndRejectedListsAskForTheirOwnStatus() {
        Provider approved = provider(1L, ApprovalStatus.APPROVED);
        Provider rejected = provider(2L, ApprovalStatus.REJECTED);
        when(providerRepository.findResolvedByStatus(ApprovalStatus.APPROVED)).thenReturn(List.of(approved));
        when(providerRepository.findResolvedByStatus(ApprovalStatus.REJECTED)).thenReturn(List.of(rejected));

        assertThat(service.findApprovedProviders()).containsExactly(approved);
        assertThat(service.findRejectedProviders()).containsExactly(rejected);
    }
}
