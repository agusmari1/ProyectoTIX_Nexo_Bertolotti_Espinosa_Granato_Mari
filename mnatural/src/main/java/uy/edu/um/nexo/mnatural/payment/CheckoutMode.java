package uy.edu.um.nexo.mnatural.payment;

// Cómo paga el turista con un servicio de pagos
public enum CheckoutMode {
    // Se lo manda a la página de pago del servicio (Mercado Pago Checkout Pro) y vuelve a la aplicación
    REDIRECT,
    // Paga en una pantalla de la aplicación con un formulario de tarjeta: el de Mercado Pago (Card Payment Brick)
    // o, con el pago simulado, uno propio de prueba
    CARD_FORM
}
