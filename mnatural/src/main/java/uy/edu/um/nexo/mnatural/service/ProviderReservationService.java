package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ProviderBookingDTO;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.ProviderBookingRepository;

import java.util.List;

// Reservas sobre las actividades del prestador con sesión iniciada (solo lectura).
// El prestador tiene que estar aprobado y las consultas filtran por su id: una actividad u horario
// de otro prestador simplemente no devuelve nada
@Service
public class ProviderReservationService {

    private final ProviderBookingRepository bookingRepository;
    private final ApprovedProviderResolver providerResolver;

    public ProviderReservationService(ProviderBookingRepository bookingRepository,
                                      ApprovedProviderResolver providerResolver) {
        this.bookingRepository = bookingRepository;
        this.providerResolver = providerResolver;
    }

    // Si viene un horario, se filtra por ese horario; si no y viene una actividad, por la actividad; si no, todas
    @Transactional(readOnly = true)
    public List<ProviderBookingDTO> findReservations(String providerEmail, Long activityId, Long scheduleId) {
        Provider provider = providerResolver.require(providerEmail);
        List<Booking> bookings;
        if (scheduleId != null) {
            bookings = bookingRepository.findByScheduleOfProvider(provider.getId(), scheduleId);
        } else if (activityId != null) {
            bookings = bookingRepository.findByActivityOfProvider(provider.getId(), activityId);
        } else {
            bookings = bookingRepository.findAllOfProvider(provider.getId());
        }
        return bookings.stream().map(ProviderBookingDTO::from).toList();
    }
}
