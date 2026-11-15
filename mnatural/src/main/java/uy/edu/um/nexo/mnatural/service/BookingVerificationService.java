package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.BookingVerificationDTO;
import uy.edu.um.nexo.mnatural.dto.ProviderBookingDTO;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.ProviderBookingRepository;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

// El prestador verifica el código de reserva (MN-XXXXXXXX) que le muestra el turista, escrito a mano o leído del QR
// (el QR contiene solo el código). Es válido únicamente si la reserva es de una actividad de ESE prestador y
// está confirmada. Un código que no existe y uno de otro prestador dan el mismo mensaje, para no revelar
// qué códigos existen
@Service
public class BookingVerificationService {

    private static final Pattern CODE_FORMAT = Pattern.compile("MN-[A-Z0-9]{8}");
    static final String NOT_VALID = "El código no corresponde a ninguna reserva de tus actividades.";

    private final ProviderBookingRepository bookingRepository;
    private final ApprovedProviderResolver providerResolver;

    public BookingVerificationService(ProviderBookingRepository bookingRepository,
                                      ApprovedProviderResolver providerResolver) {
        this.bookingRepository = bookingRepository;
        this.providerResolver = providerResolver;
    }

    @Transactional(readOnly = true)
    public BookingVerificationDTO verify(String providerEmail, String rawCode) {
        Provider provider = providerResolver.require(providerEmail);

        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            return BookingVerificationDTO.invalid("Ingresá el código de la reserva.");
        }
        if (!CODE_FORMAT.matcher(code).matches()) {
            return BookingVerificationDTO.invalid("El código no tiene el formato correcto (MN- y 8 letras o números).");
        }

        Optional<Booking> found = bookingRepository.findByCodeOfProvider(provider.getId(), code);
        if (found.isEmpty()) {
            return BookingVerificationDTO.invalid(NOT_VALID);
        }

        Booking booking = found.get();
        var schedule = booking.getActivitySchedule();
        var activity = schedule.getActivity();
        String statusLabel = ProviderBookingDTO.statusLabel(booking.getStatus());
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            // Es una reserva suya, pero no está confirmada: se informa el estado sin datos del turista
            return BookingVerificationDTO.invalid("La reserva existe pero está " + statusLabel.toLowerCase(Locale.ROOT)
                            + ": no es válida para participar.",
                    code, activity.getName(), schedule.getStartDateTime(), statusLabel);
        }

        var tourist = booking.getTourist();
        return new BookingVerificationDTO(true, "Reserva válida.", code, activity.getName(),
                schedule.getStartDateTime(), tourist.getName() + " " + tourist.getLastName(),
                booking.getParticipantCount(), statusLabel);
    }
}
