package uy.edu.um.nexo.mnatural.weather;

import java.time.LocalDate;

// Pronóstico de un día. La probabilidad de lluvia y la descripción pueden faltar si el servicio no las informó
public record WeatherForecast(LocalDate date,
                              int minTemperature,
                              int maxTemperature,
                              Integer rainProbability,
                              String description) {
}
