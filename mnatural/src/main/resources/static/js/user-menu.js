// El menú de usuario se abre y se cierra solo con <details>. Esto suma que también
// se cierre al tocar afuera o con la tecla Escape
document.addEventListener('click', function (event) {
    const menu = document.querySelector('.user-menu[open]');
    if (menu && !menu.contains(event.target)) {
        menu.removeAttribute('open');
    }
});

document.addEventListener('keydown', function (event) {
    const menu = document.querySelector('.user-menu[open]');
    if (event.key === 'Escape' && menu) {
        menu.removeAttribute('open');
        menu.querySelector('summary').focus();
    }
});
