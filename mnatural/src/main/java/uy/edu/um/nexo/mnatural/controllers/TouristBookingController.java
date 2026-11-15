package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;
import uy.edu.um.nexo.mnatural.service.BookingService;
import uy.edu.um.nexo.mnatural.service.TouristService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// "Mis reservas" del turista logueado. Solo para turistas ("/turista/**" en SecurityConfig)
@Controller
public class TouristBookingController {

    private final BookingService bookingService;
    private final TouristService touristService;
    // Opcional: en un test del controller que no lo carga, simplemente no se marca ninguna reserva como valorada
    private final ObjectProvider<ReviewRepository> reviewRepository;

    public TouristBookingController(BookingService bookingService, TouristService touristService,
                                    ObjectProvider<ReviewRepository> reviewRepository) {
        this.bookingService = bookingService;
        this.touristService = touristService;
        this.reviewRepository = reviewRepository;
    }

    @GetMapping("/turista/reservas")
    public String myBookings(Authentication authentication, Model model) {
        Long touristId = touristService.getByEmail(authentication.getName()).getId();
        List<TouristBookingDTO> bookings = bookingService.findBookingsOf(touristId);
        LocalDateTime now = LocalDateTime.now();

        model.addAttribute("bookings", bookings);
        // Próximas: la actividad todavía no empezó. Pasadas: ya empezó o ya pasó (cada lista conserva el orden de la
        // original, de la más reciente a la más antigua)
        model.addAttribute("upcoming", bookings.stream().filter(b -> !b.startDateTime().isBefore(now)).toList());
        model.addAttribute("past", bookings.stream().filter(b -> b.startDateTime().isBefore(now)).toList());
        model.addAttribute("reviewedIds", reviewedBookingIds(touristId));
        return "reservas/list";
    }

    // Solo las reservas DE ESTE turista (la consulta filtra por su id)
    private Set<Long> reviewedBookingIds(Long touristId) {
        ReviewRepository repository = reviewRepository.getIfAvailable();
        if (repository == null) {
            return Set.of();
        }
        return new HashSet<>(repository.findReviewedBookingIdsOf(touristId));
    }
}
