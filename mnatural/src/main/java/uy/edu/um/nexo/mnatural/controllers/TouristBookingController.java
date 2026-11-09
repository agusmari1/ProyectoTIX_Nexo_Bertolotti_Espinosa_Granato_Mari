package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.nexo.mnatural.service.BookingService;
import uy.edu.um.nexo.mnatural.service.TouristService;

// "Mis reservas" del turista logueado. Solo para turistas ("/turista/**" en SecurityConfig)
@Controller
public class TouristBookingController {

    private final BookingService bookingService;
    private final TouristService touristService;

    public TouristBookingController(BookingService bookingService, TouristService touristService) {
        this.bookingService = bookingService;
        this.touristService = touristService;
    }

    @GetMapping("/turista/reservas")
    public String myBookings(Authentication authentication, Model model) {
        Long touristId = touristService.getByEmail(authentication.getName()).getId();
        model.addAttribute("bookings", bookingService.findBookingsOf(touristId));
        return "reservas/list";
    }
}
