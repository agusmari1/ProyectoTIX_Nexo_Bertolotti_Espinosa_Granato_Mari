package uy.edu.um.nexo.mnatural.exception;

public class NotEnoughSpotsException extends BookingException {

    private final int availableSpots;
    private final int requestedSpots;

    public NotEnoughSpotsException(int availableSpots, int requestedSpots) {
        super(availableSpots == 0
                ? "No quedan lugares disponibles para esta fecha."
                : "Solo quedan " + availableSpots + " lugares disponibles para esta fecha y pediste " + requestedSpots + ".");
        this.availableSpots = availableSpots;
        this.requestedSpots = requestedSpots;
    }

    public int getAvailableSpots() {
        return availableSpots;
    }

    public int getRequestedSpots() {
        return requestedSpots;
    }
}
