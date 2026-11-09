package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ProfileBusinessDTO;
import uy.edu.um.nexo.mnatural.dto.ProfileDTO;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.security.Role;

import java.util.List;
import java.util.Optional;

// Arma lo que muestra /perfil para el usuario con la sesión iniciada. Por ahora es solo para ver
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final BookingService bookingService;

    public ProfileService(UserRepository userRepository, BookingService bookingService) {
        this.userRepository = userRepository;
        this.bookingService = bookingService;
    }

    // Vacío si el usuario ya no existe (por ejemplo, lo borraron con la sesión abierta)
    @Transactional(readOnly = true)
    public Optional<ProfileDTO> findProfile(String email) {
        return userRepository.findByEmail(email).map(this::toProfile);
    }

    private ProfileDTO toProfile(User user) {
        Role role = Role.ADMIN;
        String exceptionalCondition = null;
        ApprovalStatus approvalStatus = null;
        List<ProfileBusinessDTO> businesses = List.of();
        List<TouristBookingDTO> bookings = List.of();

        if (user instanceof Tourist tourist) {
            role = Role.TOURIST;
            exceptionalCondition = tourist.getExceptionalCondition();
            bookings = bookingService.findBookingsOf(tourist.getId());
        } else if (user instanceof Provider provider) {
            role = Role.PROVIDER;
            approvalStatus = provider.getApprovalStatus();
            businesses = provider.getBusinesses().stream().map(ProfileBusinessDTO::from).toList();
        }

        return new ProfileDTO(role, user.getName(), user.getLastName(), user.getEmail(), user.getDateOfBirth(),
                user.getDocumentType(), user.getDocumentNumber(), user.getCountry(), user.getPhoneNumber(),
                exceptionalCondition, approvalStatus, businesses, bookings);
    }
}
