package uy.edu.um.nexo.mnatural.exception;

// La actividad no existe o es de otro prestador. Se usa el mismo error en los dos casos
// para no revelar qué actividades existen
public class ActivityNotFoundException extends RuntimeException {

    public ActivityNotFoundException(Long activityId) {
        super("No encontramos la actividad " + activityId + " entre tus actividades.");
    }
}
