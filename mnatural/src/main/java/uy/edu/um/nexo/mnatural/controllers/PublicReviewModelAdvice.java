package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewViewDTO;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.util.List;
import java.util.Map;

// Agrega las reseñas a las pantallas del portal público (listado y detalle), que las muestran con
// el fragmento templates/fragments/reviews.html. Está aparte para no modificar PublicActivityController
// ReviewService llega con ObjectProvider: los @WebMvcTest cargan todos los @ControllerAdvice, y así los tests
// de otros controllers no necesitan agregar un mock de ReviewService (sin él, el advice devuelve listas vacías)
@ControllerAdvice(assignableTypes = PublicActivityController.class)
public class PublicReviewModelAdvice {

    private final ObjectProvider<ReviewService> reviewService;

    public PublicReviewModelAdvice(ObjectProvider<ReviewService> reviewService) {
        this.reviewService = reviewService;
    }

    // Id de actividad -> promedio y cantidad (una sola consulta para todas las tarjetas del listado)
    @ModelAttribute("reviewSummaries")
    public Map<Long, ReviewSummaryDTO> reviewSummaries() {
        ReviewService service = reviewService.getIfAvailable();
        return service == null ? Map.of() : service.summariesByActivity();
    }

    // Las reseñas de la actividad del detalle (/actividades/{id}); en el listado no hay id
    @ModelAttribute("activityReviews")
    public List<ReviewViewDTO> activityReviews(@PathVariable(name = "id", required = false) Long activityId) {
        ReviewService service = reviewService.getIfAvailable();
        return activityId == null || service == null ? List.of() : service.findVisibleReviews(activityId);
    }
}
