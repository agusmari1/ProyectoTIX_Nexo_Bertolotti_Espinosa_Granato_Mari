package uy.edu.um.nexo.mnatural.payment;

import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Formulario de tarjeta de PRUEBA del pago simulado: no se conecta a nada
class SimulatedCardPaymentTest {

    private static final BigDecimal AMOUNT = new BigDecimal("3000.00");

    private final SimulatedPaymentGateway gateway =
            new SimulatedPaymentGateway(PaymentStatus.APPROVED, "http://localhost:8080");

    private Booking booking() {
        Activity activity = new Activity();
        activity.setName("Cabalgata");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));
        Booking booking = new Booking();
        booking.setId(46L);
        booking.setActivitySchedule(schedule);
        return booking;
    }

    private static String nextYear() {
        return "11/" + (LocalDate.now().getYear() + 2);
    }

    private static CardPaymentRequest card(String holder, String number, String expiry, String code) {
        return new CardPaymentRequest(null, null, null, null, null, null, "12345678", number, holder, expiry, code);
    }

    private GatewayPaymentStatus pay(String holder) {
        return gateway.payWithCard(booking(), "MN-46-" + holder, AMOUNT,
                card(holder, "4509 9535 6623 3704", nextYear(), "123"));
    }

    @Test
    void theSimulatedServiceUsesTheCardForm() {
        assertThat(gateway.checkoutMode()).isEqualTo(CheckoutMode.CARD_FORM);
        // Sin clave pública: el formulario es el propio de la aplicación
        assertThat(gateway.publicKey()).isNull();
    }

    @Test
    void theHolderNameChoosesTheResultLikeInTheMercadoPagoSandbox() {
        assertThat(pay("APRO").status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pay("OTHE").status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(pay("FUND").status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(pay("CONT").status()).isEqualTo(PaymentStatus.PENDING);
        // Cualquier otro nombre aprueba, sin importar mayúsculas ni espacios
        assertThat(pay("  Ana Pérez ").status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pay("apro").status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pay("fund").failureReason()).contains("fondos");
    }

    @Test
    void onlyTheBrandAndTheLastFourDigitsAreKept() {
        GatewayPaymentStatus result = pay("APRO");

        assertThat(result.cardBrand()).isEqualTo("visa");
        assertThat(result.cardLastFour()).isEqualTo("3704");
        assertThat(result.toString()).doesNotContain("4509").doesNotContain("9535").doesNotContain("123");
        assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(result.currency()).isEqualTo("UYU");
    }

    @Test
    void theBrandIsRecognizedFromTheFirstDigit() {
        assertThat(gateway.payWithCard(booking(), "MN-46-a", AMOUNT,
                card("APRO", "5031 7557 3453 0604", nextYear(), "123")).cardBrand()).isEqualTo("master");
        assertThat(gateway.payWithCard(booking(), "MN-46-b", AMOUNT,
                card("APRO", "3782 8224 6310 005", nextYear(), "1234")).cardBrand()).isEqualTo("tarjeta");
    }

    @Test
    void invalidCardDataIsRejectedWithAClearReasonAndNoCardInfo() {
        for (CardPaymentRequest bad : new CardPaymentRequest[]{
                card("APRO", "1234", nextYear(), "123"),
                card("APRO", "abcd efgh ijkl mnop", nextYear(), "123"),
                card("APRO", null, nextYear(), "123"),
                card("APRO", "4509 9535 6623 3704", "13/30", "123"),
                card("APRO", "4509 9535 6623 3704", "01/20", "123"),
                card("APRO", "4509 9535 6623 3704", null, "123"),
                card("APRO", "4509 9535 6623 3704", nextYear(), "12"),
                card("APRO", "4509 9535 6623 3704", nextYear(), null)}) {
            GatewayPaymentStatus result = gateway.payWithCard(booking(), "MN-46-x", AMOUNT, bad);

            assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(result.failureReason()).contains("Revisá los datos");
            assertThat(result.cardLastFour()).isNull();
        }
    }

    @Test
    void theResultIsRememberedSoALaterQueryDoesNotTurnARejectionIntoAnApproval() {
        gateway.payWithCard(booking(), "MN-46-rej", AMOUNT, card("OTHE", "4509 9535 6623 3704", nextYear(), "123"));
        gateway.payWithCard(booking(), "MN-46-pend", AMOUNT, card("CONT", "4509 9535 6623 3704", nextYear(), "123"));

        assertThat(gateway.getPaymentStatus("MN-46-rej").status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(gateway.getPaymentStatus("MN-46-pend").status()).isEqualTo(PaymentStatus.PENDING);
        // Una referencia que no vino del formulario se sigue resolviendo como antes
        assertThat(gateway.getPaymentStatus("SIM-otra").status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void whenTheSimulatedServiceIsForcedToErrorTheCardPaymentFails() {
        SimulatedPaymentGateway down = new SimulatedPaymentGateway(PaymentStatus.ERROR, "http://localhost:8080");

        assertThatThrownBy(() -> down.payWithCard(booking(), "MN-46-e", AMOUNT,
                card("APRO", "4509 9535 6623 3704", nextYear(), "123"))).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void anExpiryInTheCurrentMonthIsStillValidAndLastMonthIsNot() {
        LocalDate now = LocalDate.now();

        assertThat(SimulatedPaymentGateway.validExpiry(String.format("%02d/%02d", now.getMonthValue(), now.getYear() % 100)))
                .isTrue();
        assertThat(SimulatedPaymentGateway.validExpiry(String.format("%02d/%d", now.getMonthValue(), now.getYear() + 1)))
                .isTrue();
        LocalDate lastMonth = now.minusMonths(1);
        assertThat(SimulatedPaymentGateway.validExpiry(String.format("%02d/%02d", lastMonth.getMonthValue(),
                lastMonth.getYear() % 100))).isFalse();
    }
}
