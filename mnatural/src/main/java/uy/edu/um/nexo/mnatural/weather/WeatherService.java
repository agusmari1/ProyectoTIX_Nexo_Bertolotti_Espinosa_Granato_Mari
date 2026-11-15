package uy.edu.um.nexo.mnatural.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Pronóstico del tiempo para el detalle de una actividad, con Open-Meteo (https://open-meteo.com), que no pide clave.
// Es un dato "de cortesía": NUNCA tiene que romper la pantalla. Si el servicio falla, tarda, responde algo raro o la
// fecha queda fuera del rango que pronostica, simplemente no devuelve nada y la pantalla no muestra el bloque
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude={latitude}&longitude={longitude}"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            + "&timezone=America/Montevideo&forecast_days=16";

    // Open-Meteo pronostica hasta 16 días contando hoy
    static final int FORECAST_DAYS = 16;
    // Máximo de fechas que se muestran en el detalle
    static final int MAX_DAYS_SHOWN = 3;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    // El pronóstico no cambia minuto a minuto: se reutiliza un rato. Si falló, se espera menos antes de reintentar
    private static final Duration SUCCESS_TTL = Duration.ofMinutes(30);
    private static final Duration FAILURE_TTL = Duration.ofMinutes(5);

    private record CacheEntry(Instant expiresAt, Map<LocalDate, WeatherForecast> days) {
    }

    private final RestClient restClient;
    private final Clock clock;
    private final boolean enabled;
    private final Map<Department, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public WeatherService(@Value("${weather.enabled:true}") boolean enabled) {
        this(RestClient.builder().requestFactory(requestFactory()), Clock.system(ZoneId.of("America/Montevideo")),
                enabled);
    }

    // Para los tests: permite conectar un servidor de mentira al builder y controlar la hora
    WeatherService(RestClient.Builder builder, Clock clock, boolean enabled) {
        this.restClient = builder.build();
        this.clock = clock;
        this.enabled = enabled;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    // Pronóstico de los días en que la actividad tiene horarios (hasta MAX_DAYS_SHOWN, en orden). Solo salen los
    // días que Open-Meteo todavía puede pronosticar; si no hay ninguno, la lista queda vacía y no se llama al servicio
    public List<WeatherForecast> forecastsFor(Department department, Collection<LocalDateTime> startTimes) {
        try {
            if (!enabled || department == null || startTimes == null || startTimes.isEmpty()) {
                return List.of();
            }
            LocalDate today = LocalDate.now(clock);
            List<LocalDate> dates = startTimes.stream()
                    .filter(start -> start != null)
                    .map(LocalDateTime::toLocalDate)
                    .filter(date -> !date.isBefore(today) && date.isBefore(today.plusDays(FORECAST_DAYS)))
                    .distinct()
                    .sorted()
                    .limit(MAX_DAYS_SHOWN)
                    .toList();
            if (dates.isEmpty()) {
                return List.of();
            }

            Map<LocalDate, WeatherForecast> available = forecastOf(department);
            List<WeatherForecast> result = new ArrayList<>();
            for (LocalDate date : dates) {
                WeatherForecast forecast = available.get(date);
                if (forecast != null) {
                    result.add(forecast);
                }
            }
            return result;
        } catch (RuntimeException e) {
            // Última red de seguridad: pase lo que pase, el detalle de la actividad se muestra igual
            log.warn("No se pudo armar el pronóstico: {}", e.toString());
            return List.of();
        }
    }

    private Map<LocalDate, WeatherForecast> forecastOf(Department department) {
        Instant now = clock.instant();
        CacheEntry entry = cache.get(department);
        if (entry == null || !now.isBefore(entry.expiresAt())) {
            entry = fetch(department, now);
            cache.put(department, entry);
        }
        return entry.days();
    }

    // Una sola llamada trae los 16 días de la capital del departamento
    private CacheEntry fetch(Department department, Instant now) {
        DepartmentCoordinates.Coordinates coordinates = DepartmentCoordinates.of(department);
        if (coordinates == null) {
            return new CacheEntry(now.plus(FAILURE_TTL), Map.of());
        }
        try {
            Map<?, ?> body = restClient.get()
                    .uri(FORECAST_URL, coordinates.latitude(), coordinates.longitude())
                    .retrieve()
                    .body(Map.class);
            Map<LocalDate, WeatherForecast> days = parse(body);
            return new CacheEntry(now.plus(days.isEmpty() ? FAILURE_TTL : SUCCESS_TTL), days);
        } catch (Exception e) {
            // Sin internet, timeout, error del servicio, respuesta ilegible...: no hay pronóstico y listo
            log.warn("No se pudo obtener el pronóstico de {}: {}", department, e.toString());
            return new CacheEntry(now.plus(FAILURE_TTL), Map.of());
        }
    }

    private Map<LocalDate, WeatherForecast> parse(Map<?, ?> body) {
        Map<LocalDate, WeatherForecast> days = new HashMap<>();
        if (body == null || !(body.get("daily") instanceof Map<?, ?> daily) || !(daily.get("time") instanceof List<?> times)) {
            return days;
        }
        List<?> codes = daily.get("weather_code") instanceof List<?> list ? list : null;
        List<?> maxima = daily.get("temperature_2m_max") instanceof List<?> list ? list : null;
        List<?> minima = daily.get("temperature_2m_min") instanceof List<?> list ? list : null;
        List<?> rain = daily.get("precipitation_probability_max") instanceof List<?> list ? list : null;

        for (int i = 0; i < times.size(); i++) {
            Double max = numberAt(maxima, i);
            Double min = numberAt(minima, i);
            if (!(times.get(i) instanceof String text) || max == null || min == null) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(text);
                Double code = numberAt(codes, i);
                Double rainProbability = numberAt(rain, i);
                days.put(date, new WeatherForecast(date, (int) Math.round(min), (int) Math.round(max),
                        rainProbability == null ? null : (int) Math.round(rainProbability),
                        code == null ? null : describe(code.intValue())));
            } catch (RuntimeException e) {
                // Una fecha ilegible: se ignora ese día y se siguen leyendo los otros
                log.debug("Día ignorado en el pronóstico: {}", text);
            }
        }
        return days;
    }

    private static Double numberAt(List<?> list, int index) {
        if (list != null && index < list.size() && list.get(index) instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    // Códigos de tiempo de la OMM que usa Open-Meteo, en palabras
    static String describe(int code) {
        return switch (code) {
            case 0 -> "Despejado";
            case 1 -> "Mayormente despejado";
            case 2 -> "Parcialmente nublado";
            case 3 -> "Nublado";
            case 45, 48 -> "Niebla";
            case 51, 53, 55, 56, 57 -> "Llovizna";
            case 61, 63, 65, 66, 67 -> "Lluvia";
            case 71, 73, 75, 77 -> "Nieve";
            case 80, 81, 82 -> "Chubascos";
            case 85, 86 -> "Chubascos de nieve";
            case 95, 96, 99 -> "Tormenta";
            default -> "Tiempo variable";
        };
    }
}
