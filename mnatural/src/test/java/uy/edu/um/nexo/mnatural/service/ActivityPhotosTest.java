package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import uy.edu.um.nexo.mnatural.entities.Department;

import static org.assertj.core.api.Assertions.assertThat;

// Usa los archivos reales de static/img/actividades: no toca la base de datos
class ActivityPhotosTest {

    private final ActivityPhotos photos = new ActivityPhotos();

    @Test
    void aDepartmentWithAPhotoGetsItsPublicPath() {
        String url = photos.urlFor(Department.ROCHA);

        assertThat(url).startsWith("/img/actividades/rocha.");
        // La ruta pública apunta a un archivo que realmente existe
        assertThat(new ClassPathResource("static" + url).exists()).isTrue();
    }

    @Test
    void everyPhotoThatExistsIsFound() {
        assertThat(photos.urlFor(Department.MALDONADO)).startsWith("/img/actividades/punta-del-este.");
        assertThat(photos.urlFor(Department.MONTEVIDEO)).startsWith("/img/actividades/montevideo.");
        assertThat(photos.urlFor(Department.SALTO)).startsWith("/img/actividades/salto.");
    }

    @Test
    void aDepartmentWithoutPhotoGetsNullInsteadOfABrokenImage() {
        assertThat(photos.urlFor(Department.ARTIGAS)).isNull();
        assertThat(photos.urlFor(Department.TREINTA_Y_TRES)).isNull();
    }

    @Test
    void aNullDepartmentIsHarmless() {
        assertThat(photos.urlFor(null)).isNull();
    }
}
