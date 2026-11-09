package uy.edu.um.nexo.mnatural.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

// Test unitario: MockRestServiceServer hace de Open-Meteo, así que no se usa internet. La hora es fija (2027-01-10)
class WeatherServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDate TODAY = LocalDate.of(2027, 1, 10);

    private MutableClock clock;
    private MockRestServiceServer server;
    private WeatherService service;

    // Reloj que se puede adelantar para probar el vencimiento de la memoria
    private static class MutableClock extends Clock {
        private Instant now = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WeatherService(builder, clock, true);
    }

    private static String forecastJson() {
        return """
                {"daily":{
                  "time":["2027-01-10","2027-01-11","2027-01-12","2027-01-13"],
                  "weather_code":[0,61,95,3],
                  "temperature_2m_max":[28.4,24.6,22.0,26.5],
                  "temperature_2m_min":[18.2,16.5,15.0,17.4],
                  "precipitation_probability_max":[5,80,90,null]
                }}""";
    }

    private void respondOnce(String body) {
        server.expect(once(), requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static LocalDateTime at(int day) {
        return LocalDateTime.of(2027, 1, day, 9, 30);
    }

    @Test
    void itAsksOpenMeteoForTheDepartmentCapitalWithoutAnyKey() {
        server.expect(once(), requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "-34.9011"))
                .andExpect(queryParam("longitude", "-56.1645"))
                .andExpect(queryParam("timezone", "America/Montevideo"))
                .andExpect(queryParam("daily", containsString("temperature_2m_max")))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(forecastJson(), MediaType.APPLICATION_JSON));

        service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)));

        server.verify();
    }

    @Test
    void itReturnsTheForecastOfTheDaysWithSchedulesInOrder() {
        respondOnce(forecastJson());

        List<WeatherForecast> result = service.forecastsFor(Department.MONTEVIDEO, List.of(at(12), at(11), at(12)));

        assertThat(result).extracting(WeatherForecast::date)
                .containsExactly(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 12));
        WeatherForecast rainy = result.getFirst();
        assertThat(rainy.maxTemperature()).isEqualTo(25);
        assertThat(rainy.minTemperature()).isEqualTo(17);
        assertThat(rainy.rainProbability()).isEqualTo(80);
        assertThat(rainy.description()).isEqualTo("Lluvia");
        assertThat(result.get(1).description()).isEqualTo("Tormenta");
    }

    @Test
    void aMissingRainProbabilityIsLeftEmptyInsteadOfFailing() {
        respondOnce(forecastJson());

        List<WeatherForecast> result = service.forecastsFor(Department.MONTEVIDEO, List.of(at(13)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().rainProbability()).isNull();
        assertThat(result.getFirst().description()).isEqualTo("Nublado");
    }

    @Test
    void atMostThreeDaysAreShown() {
        respondOnce("""
                {"daily":{"time":["2027-01-10","2027-01-11","2027-01-12","2027-01-13"],
                "weather_code":[0,0,0,0],"temperature_2m_max":[20,20,20,20],"temperature_2m_min":[10,10,10,10],
                "precipitation_probability_max":[0,0,0,0]}}""");

        List<WeatherForecast> result = service.forecastsFor(Department.SALTO,
                List.of(at(10), at(11), at(12), at(13)));

        assertThat(result).hasSize(WeatherService.MAX_DAYS_SHOWN);
    }

    @Test
    void datesBeyondTheForecastRangeShowNothingAndDoNotCallTheService() {
        // Hoy es el 10/01: con 16 días de pronóstico, el 26/01 ya queda afuera
        List<WeatherForecast> result = service.forecastsFor(Department.MONTEVIDEO,
                List.of(LocalDateTime.of(2027, 1, 26, 9, 0), LocalDateTime.of(2027, 3, 1, 9, 0)));

        assertThat(result).isEmpty();
        server.verify(); // sin ningún pedido esperado: si se hubiera llamado, fallaría
    }

    @Test
    void pastDatesAreIgnored() {
        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(LocalDateTime.of(2027, 1, 9, 9, 0)))).isEmpty();
        server.verify();
    }

    @Test
    void theLastDayInsideTheRangeIsStillForecast() {
        respondOnce("""
                {"daily":{"time":["2027-01-25"],"weather_code":[1],"temperature_2m_max":[30],
                "temperature_2m_min":[20],"precipitation_probability_max":[10]}}""");

        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(LocalDateTime.of(2027, 1, 25, 9, 0))))
                .hasSize(1);
    }

    @Test
    void aServerErrorGivesNoForecastAndDoesNotThrow() {
        server.expect(once(), requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withServerError());

        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).isEmpty();
    }

    @Test
    void aNetworkFailureOrTimeoutGivesNoForecastAndDoesNotThrow() {
        server.expect(once(), requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withException(new IOException("Read timed out")));

        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).isEmpty();
    }

    @Test
    void anUnreadableOrUnexpectedResponseGivesNoForecast() {
        respondOnce("esto no es json");
        assertThat(service.forecastsFor(Department.ROCHA, List.of(at(11)))).isEmpty();

        server.reset();
        respondOnce("{\"otra\":\"cosa\"}");
        assertThat(service.forecastsFor(Department.RIVERA, List.of(at(11)))).isEmpty();

        server.reset();
        respondOnce("{\"daily\":{\"time\":[\"no-es-fecha\",\"2027-01-11\"],\"temperature_2m_max\":[20,21],"
                + "\"temperature_2m_min\":[10,11]}}");
        // La fecha rota se ignora y la buena se aprovecha, aunque falten código y lluvia
        List<WeatherForecast> partial = service.forecastsFor(Department.SALTO, List.of(at(11)));
        assertThat(partial).hasSize(1);
        assertThat(partial.getFirst().description()).isNull();
        assertThat(partial.getFirst().rainProbability()).isNull();
    }

    @Test
    void theForecastIsReusedForAWhileInsteadOfCallingTheServiceEveryTime() {
        respondOnce(forecastJson()); // solo se espera UN pedido

        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).hasSize(1);
        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(12)))).hasSize(1);

        server.verify();
    }

    @Test
    void aFailureIsNotRetriedRightAwayButIsAfterAFewMinutes() {
        server.expect(once(), requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withServerError());
        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).isEmpty();

        // Dentro de los 5 minutos no vuelve a llamar (si lo hiciera, no hay otro pedido esperado y fallaría)
        server.reset();
        clock.advanceMinutes(2);
        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).isEmpty();

        // Pasados los 5 minutos reintenta y esta vez anda
        clock.advanceMinutes(4);
        respondOnce(forecastJson());
        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).hasSize(1);
        server.verify();
    }

    @Test
    void whenDisabledNothingIsShownAndTheServiceIsNeverCalled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer other = MockRestServiceServer.bindTo(builder).build();
        WeatherService disabled = new WeatherService(builder, clock, false);

        assertThat(disabled.forecastsFor(Department.MONTEVIDEO, List.of(at(11)))).isEmpty();
        other.verify();
    }

    @Test
    void emptyOrNullArgumentsAreHarmless() {
        assertThat(service.forecastsFor(null, List.of(at(11)))).isEmpty();
        assertThat(service.forecastsFor(Department.MONTEVIDEO, List.of())).isEmpty();
        assertThat(service.forecastsFor(Department.MONTEVIDEO, null)).isEmpty();
        server.verify();
    }

    @Test
    void everyDepartmentHasCoordinatesInsideUruguay() {
        for (Department department : Department.values()) {
            DepartmentCoordinates.Coordinates c = DepartmentCoordinates.of(department);
            assertThat(c).as(department.name()).isNotNull();
            assertThat(c.latitude()).as(department.name()).isBetween(-35.1, -30.0);
            assertThat(c.longitude()).as(department.name()).isBetween(-58.5, -53.0);
        }
    }

    @Test
    void weatherCodesAreDescribedInSpanish() {
        assertThat(WeatherService.describe(0)).isEqualTo("Despejado");
        assertThat(WeatherService.describe(63)).isEqualTo("Lluvia");
        assertThat(WeatherService.describe(95)).isEqualTo("Tormenta");
        assertThat(WeatherService.describe(1234)).isEqualTo("Tiempo variable");
    }
}
