// Pantalla de pago (payments/pay.html). Maneja los dos formularios de tarjeta:
//   - con la clave pública de Mercado Pago (data-public-key): el formulario de Mercado Pago (Card Payment Brick),
//     que pregunta tarjeta de crédito o débito y pide los datos. La tarjeta se convierte en un token en el navegador:
//     el número nunca llega a nuestro servidor;
//   - sin clave pública (pago simulado): un formulario propio de PRUEBA.
// En los dos casos se manda el pago a POST /payments/bookings/{id}/card y se muestra el resultado.
(function () {
    'use strict';

    var root = document.getElementById('checkout');
    if (!root) {
        return;
    }
    var bookingId = root.dataset.booking;
    var amount = Number(root.dataset.amount);
    var publicKey = root.dataset.publicKey;
    var messageBox = document.getElementById('checkout-message');
    // Los textos de los avisos llegan ya traducidos en atributos data-msg-* de #checkout (el idioma de la página)
    var messages = root.dataset;
    var csrfToken = metaContent('_csrf');
    var csrfHeader = metaContent('_csrf_header');

    function metaContent(name) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute('content') : null;
    }

    // textContent y no innerHTML: el texto viene del servidor y no se interpreta como HTML
    function show(kind, text) {
        messageBox.className = 'alert alert-' + kind;
        messageBox.textContent = text;
        messageBox.hidden = false;
        messageBox.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    function sendPayment(payload) {
        var headers = {'Content-Type': 'application/json'};
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }
        return fetch('/payments/bookings/' + bookingId + '/card', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        }).then(function (response) {
            return response.json().catch(function () {
                return {status: 'ERROR', message: messages.msgProcessError};
            });
        }).catch(function () {
            return {status: 'ERROR', message: messages.msgConnectionError};
        });
    }

    // Devuelve true si el pago quedó aprobado o en proceso (se vuelve a la reserva) y false si hay que reintentar
    function handleResult(data) {
        if (data.status === 'APPROVED') {
            show('success', data.message);
        } else if (data.status === 'PENDING') {
            show('info', data.message);
        } else {
            show('danger', data.message || messages.msgTryAgain);
            return false;
        }
        setTimeout(function () {
            window.location.href = data.redirectUrl;
        }, 2000);
        return true;
    }

    // ===== Formulario de Mercado Pago =====
    if (publicKey) {
        if (typeof MercadoPago === 'undefined') {
            show('danger', messages.msgSdkError);
            return;
        }
        // El formulario de Mercado Pago también sale en el idioma elegido
        var mp = new MercadoPago(publicKey, {locale: document.documentElement.lang === 'en' ? 'en-US' : 'es-UY'});
        mp.bricks().create('cardPayment', 'cardPaymentBrick_container', {
            initialization: {amount: amount},
            customization: {paymentMethods: {minInstallments: 1, maxInstallments: 1}},
            callbacks: {
                onReady: function () {
                },
                // Tiene que devolver una promesa: se resuelve si se pagó y se rechaza si hay que reintentar
                onSubmit: function (cardFormData) {
                    return new Promise(function (resolve, reject) {
                        sendPayment(cardFormData).then(function (data) {
                            return handleResult(data) ? resolve() : reject();
                        });
                    });
                },
                onError: function (error) {
                    console.error(error);
                    show('danger', messages.msgFormError);
                }
            }
        });
        return;
    }

    // ===== Formulario propio de prueba (pago simulado) =====
    var form = document.getElementById('simulated-card-form');
    if (!form) {
        return;
    }
    var cardNumber = document.getElementById('cardNumber');
    var expiry = document.getElementById('expiry');
    var submit = document.getElementById('simulated-submit');

    // Agrupa el número de a 4 y agrega la barra del vencimiento mientras se escribe
    cardNumber.addEventListener('input', function () {
        var digits = cardNumber.value.replace(/\D/g, '').slice(0, 19);
        cardNumber.value = digits.replace(/(.{4})/g, '$1 ').trim();
    });
    expiry.addEventListener('input', function () {
        var digits = expiry.value.replace(/\D/g, '').slice(0, 4);
        expiry.value = digits.length > 2 ? digits.slice(0, 2) + '/' + digits.slice(2) : digits;
    });

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        submit.disabled = true;
        sendPayment({
            holderName: document.getElementById('holderName').value,
            cardNumber: cardNumber.value,
            expiry: expiry.value,
            securityCode: document.getElementById('securityCode').value,
            documentNumber: document.getElementById('documentNumber').value
        }).then(function (data) {
            // Si se aprobó queda deshabilitado mientras se vuelve a la reserva; si no, se puede reintentar
            if (!handleResult(data)) {
                submit.disabled = false;
            }
        });
    });
})();
