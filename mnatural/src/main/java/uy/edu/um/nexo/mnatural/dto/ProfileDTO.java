package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.security.Role;

import java.time.LocalDate;
import java.util.List;

// Lo que muestra /perfil. Se arma campo por campo a propósito: la contraseña y su hash no están acá.
// Según el rol, approvalStatus y businesses son del prestador, y bookings y
// exceptionalCondition son del turista. Los que no corresponden quedan vacíos
public record ProfileDTO(Role role, String name, String lastName, String email, LocalDate dateOfBirth,
                         DocumentType documentType, String documentNumber, String country, String phoneNumber,
                         String exceptionalCondition, ApprovalStatus approvalStatus,
                         List<ProfileBusinessDTO> businesses, List<TouristBookingDTO> bookings) {
}
