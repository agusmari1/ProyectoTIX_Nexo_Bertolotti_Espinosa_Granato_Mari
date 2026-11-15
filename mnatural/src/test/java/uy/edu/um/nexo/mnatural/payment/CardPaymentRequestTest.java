package uy.edu.um.nexo.mnatural.payment;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CardPaymentRequestTest {

    @Test
    void itReadsWhatTheMercadoPagoCardFormSends() {
        Map<String, Object> body = Map.of(
                "token", "tok_123",
                "issuer_id", "24",
                "payment_method_id", "master",
                "transaction_amount", 3000,
                "installments", 1,
                "payer", Map.of("email", "ana@ejemplo.com", "identification", Map.of("type", "CI", "number", "12345678")));

        CardPaymentRequest request = CardPaymentRequest.fromJson(body);

        assertThat(request.token()).isEqualTo("tok_123");
        assertThat(request.issuerId()).isEqualTo("24");
        assertThat(request.paymentMethodId()).isEqualTo("master");
        assertThat(request.installments()).isEqualTo(1);
        assertThat(request.payerEmail()).isEqualTo("ana@ejemplo.com");
        assertThat(request.identificationType()).isEqualTo("CI");
        assertThat(request.identificationNumber()).isEqualTo("12345678");
        assertThat(request.cardNumber()).isNull();
    }

    @Test
    void itReadsTheSimulatedCardForm() {
        Map<String, Object> body = Map.of("holderName", "APRO", "cardNumber", "4509 9535 6623 3704",
                "expiry", "11/30", "securityCode", "123", "documentNumber", "12345678");

        CardPaymentRequest request = CardPaymentRequest.fromJson(body);

        assertThat(request.holderName()).isEqualTo("APRO");
        assertThat(request.cardNumber()).isEqualTo("4509 9535 6623 3704");
        assertThat(request.expiry()).isEqualTo("11/30");
        assertThat(request.securityCode()).isEqualTo("123");
        assertThat(request.identificationNumber()).isEqualTo("12345678");
        assertThat(request.token()).isNull();
    }

    @Test
    void blankOrWrongValuesBecomeNullInsteadOfFailing() {
        CardPaymentRequest request = CardPaymentRequest.fromJson(Map.of("token", "  ", "installments", "muchas",
                "payer", "no es un objeto"));

        assertThat(request.token()).isNull();
        assertThat(request.installments()).isNull();
        assertThat(request.payerEmail()).isNull();
        assertThat(CardPaymentRequest.fromJson(Map.of()).paymentMethodId()).isNull();
    }

    @Test
    void theCardNumberAndTheSecurityCodeNeverShowUpWhenItIsPrinted() {
        CardPaymentRequest request = new CardPaymentRequest("tok", "visa", null, 1, "a@b.com", "CI", "123",
                "4509953566233704", "APRO", "11/30", "987");

        assertThat(request.toString()).doesNotContain("4509").doesNotContain("3704").doesNotContain("987")
                .doesNotContain("tok");
    }

    @Test
    void thePayerEmailCanBeCompletedWithoutLosingTheRest() {
        CardPaymentRequest request = new CardPaymentRequest("tok", "visa", "24", 1, null, "CI", "123",
                null, null, null, null);

        CardPaymentRequest completed = request.withPayerEmail("ana@ejemplo.com");

        assertThat(completed.payerEmail()).isEqualTo("ana@ejemplo.com");
        assertThat(completed.token()).isEqualTo("tok");
        assertThat(completed.issuerId()).isEqualTo("24");
    }
}
