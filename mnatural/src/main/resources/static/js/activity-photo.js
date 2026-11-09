// Avisa enseguida si la foto elegida no sirve, sin esperar a enviar el formulario.
// Es solo una comodidad: la validación que cuenta está en ActivityPhotoStorage
(function () {
    const MAX_BYTES = 5 * 1024 * 1024;
    const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

    const input = document.getElementById('photo');
    const error = document.getElementById('photo-browser-error');
    if (!input || !error) {
        return;
    }

    input.addEventListener('change', function () {
        const file = input.files[0];
        let message = '';
        if (file && !ALLOWED_TYPES.includes(file.type)) {
            message = 'La foto tiene que ser una imagen JPG, PNG o WEBP.';
        } else if (file && file.size > MAX_BYTES) {
            message = 'La foto no puede superar los 5 MB.';
        }
        error.textContent = message;
        input.classList.toggle('is-invalid', message !== '');
        if (message) {
            input.value = '';
        }
    });
})();
