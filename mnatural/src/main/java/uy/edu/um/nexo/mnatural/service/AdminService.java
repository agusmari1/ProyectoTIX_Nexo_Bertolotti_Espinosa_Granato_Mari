package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.InvalidRejectionReasonException;
import uy.edu.um.nexo.mnatural.exception.ProviderNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ProviderNotPendingException;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    public static final int MAX_REJECTION_REASON_LENGTH = 500;

    private final ProviderRepository providerRepository;

    public AdminService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    // Prestadores pendientes, del que llegó primero al que llegó último
    @Transactional(readOnly = true)
    public List<Provider> findPendingProviders() {
        return providerRepository.findByApprovalStatusOrderByRequestedAtAscIdAsc(ApprovalStatus.PENDING);
    }

    @Transactional
    public Provider approve(Long providerId, Admin admin) {
        Provider provider = findPendingProvider(providerId);
        resolve(provider, ApprovalStatus.APPROVED, admin);
        // TODO: avisarle al prestador por correo electrónico que fue aprobado (fuera de esta tarea)
        return providerRepository.save(provider);
    }

    @Transactional
    public Provider reject(Long providerId, Admin admin, String reason) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new InvalidRejectionReasonException("Escribí el motivo del rechazo.");
        }
        if (cleanReason.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new InvalidRejectionReasonException(
                    "El motivo no puede superar los " + MAX_REJECTION_REASON_LENGTH + " caracteres.");
        }

        Provider provider = findPendingProvider(providerId);
        provider.setRejectionReason(cleanReason);
        resolve(provider, ApprovalStatus.REJECTED, admin);
        // TODO: avisarle al prestador por correo electrónico que fue rechazado, con el motivo (fuera de esta tarea)
        return providerRepository.save(provider);
    }

    // Solo se puede resolver una solicitud que sigue pendiente
    private Provider findPendingProvider(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        if (provider.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new ProviderNotPendingException();
        }
        return provider;
    }

    private void resolve(Provider provider, ApprovalStatus newStatus, Admin admin) {
        provider.setApprovalStatus(newStatus);
        provider.setReviewedBy(admin);
        provider.setReviewedAt(LocalDateTime.now());
    }
}
