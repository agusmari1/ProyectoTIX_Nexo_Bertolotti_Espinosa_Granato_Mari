package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.nexo.mnatural.service.ActivityPhotos;
import uy.edu.um.nexo.mnatural.service.ReviewService;
import uy.edu.um.nexo.mnatural.weather.WeatherService;

// Pone en el modelo del portal público lo que usan el listado y el detalle de actividades sin tocar
// PublicActivityController: el ayudante de fotos por departamento y el servicio del clima.
// El clima se pide con ObjectProvider: si el servicio no existe (por ejemplo, en un test del controller que no lo
// carga) el modelo trae null y la pantalla simplemente no muestra el pronóstico, en vez de fallar
@ControllerAdvice(assignableTypes = PublicActivityController.class)
public class PublicActivityAdvice {

    private final ActivityPhotos activityPhotos = new ActivityPhotos();
    private final ObjectProvider<WeatherService> weatherService;
    private final ObjectProvider<ReviewService> reviewService;

    public PublicActivityAdvice(ObjectProvider<WeatherService> weatherService,
                                ObjectProvider<ReviewService> reviewService) {
        this.weatherService = weatherService;
        this.reviewService = reviewService;
    }

    @ModelAttribute("activityPhotos")
    public ActivityPhotos activityPhotos() {
        return activityPhotos;
    }

    @ModelAttribute("weather")
    public WeatherService weather() {
        return weatherService.getIfAvailable();
    }

    // Valoraciones de la actividad en su detalle. Null si el servicio no existe: el bloque simplemente no se muestra
    @ModelAttribute("reviewService")
    public ReviewService reviewService() {
        return reviewService.getIfAvailable();
    }
}
