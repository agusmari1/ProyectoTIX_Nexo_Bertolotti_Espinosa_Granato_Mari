package uy.edu.um.nexo.mnatural.payment;

import java.util.Map;

// Datos para cobrar con tarjeta desde el formulario de pago. Hay dos orígenes:
//   - Mercado Pago (Card Payment Brick): la tarjeta ya viene convertida en un token (token, paymentMethodId,
//     issuerId, installments y el pagador), nunca el número;
//   - pago simulado: los datos de una tarjeta de PRUEBA (cardNumber, holderName, expiry, securityCode).
// El número de tarjeta y el código de seguridad no se guardan en ningún lado ni se escriben en el log:
// toString los oculta
public record CardPaymentRequest(String token, String paymentMethodId, String issuerId, Integer installments,
                                 String payerEmail, String identificationType, String identificationNumber,
                                 String cardNumber, String holderName, String expiry, String securityCode) {

    // Arma el pedido con lo que manda el formulario (un JSON). Los nombres de Mercado Pago vienen en snake_case
    public static CardPaymentRequest fromJson(Map<String, Object> body) {
        Map<?, ?> payer = body.get("payer") instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> identification = payer.get("identification") instanceof Map<?, ?> map ? map : Map.of();
        String documentNumber = text(identification.get("number"));
        return new CardPaymentRequest(
                text(body.get("token")),
                text(body.get("payment_method_id")),
                text(body.get("issuer_id")),
                integer(body.get("installments")),
                text(payer.get("email")),
                text(identification.get("type")),
                documentNumber != null ? documentNumber : text(body.get("documentNumber")),
                text(body.get("cardNumber")),
                text(body.get("holderName")),
                text(body.get("expiry")),
                text(body.get("securityCode")));
    }

    // Copia con el correo del pagador, para cuando el formulario no lo pidió
    public CardPaymentRequest withPayerEmail(String email) {
        return new CardPaymentRequest(token, paymentMethodId, issuerId, installments, email, identificationType,
                identificationNumber, cardNumber, holderName, expiry, securityCode);
    }

    @Override
    public String toString() {
        return "CardPaymentRequest[datos de la tarjeta omitidos]";
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integer(Object value) {
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
