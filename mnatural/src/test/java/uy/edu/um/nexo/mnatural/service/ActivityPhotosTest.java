package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import uy.edu.um.nexo.mnatural.entities.Activity;
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
        assertThat(photos.urlFor((Department) null)).isNull();
    }

    private static Activity activity(String name, Department department) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setDepartment(department);
        return activity;
    }

    @Test
    void thePhotoUploadedByTheProviderGoesBeforeAnyOther() {
        Activity activity = activity("Caminata por Cabo Polonio", Department.ROCHA);
        activity.setPhotoFile("abc.webp");

        assertThat(photos.urlFor(activity)).isEqualTo("/img/subidas/actividades/abc.webp");
    }

    @Test
    void anActivityWithoutAnyPhotoIsShownWithTheDefaultImage() {
        String url = photos.urlOrDefault(activity("Algo sin foto", Department.ARTIGAS));

        assertThat(url).isEqualTo(ActivityPhotos.DEFAULT_URL);
        assertThat(new ClassPathResource("static" + url).exists()).isTrue();
    }

    @Test
    void theDefaultImageIsOnlyUsedWhenThereIsNoOtherPhoto() {
        assertThat(photos.urlOrDefault(activity("City Tour", Department.MONTEVIDEO)))
                .startsWith("/img/actividades/montevideo.");
    }

    @Test
    void anActivityGetsThePhotoNamedLikeItsTitle() {
        String url = photos.urlFor(activity("Caminata por Cabo Polonio", Department.ROCHA));

        assertThat(url).isEqualTo("/img/actividades/caminata-por-cabo-polonio.jpg");
        assertThat(new ClassPathResource("static" + url).exists()).isTrue();
    }

    @Test
    void anActivityWithoutItsOwnPhotoGetsTheOneOfItsDepartment() {
        String url = photos.urlFor(activity("City Tour", Department.MONTEVIDEO));

        assertThat(url).startsWith("/img/actividades/montevideo.");
    }

    @Test
    void anActivityWithoutAnyPhotoGetsNull() {
        assertThat(photos.urlFor(activity("Algo sin foto", Department.ARTIGAS))).isNull();
        assertThat(photos.urlFor(activity(null, null))).isNull();
        assertThat(photos.urlFor((Activity) null)).isNull();
    }

    @Test
    void theNameBecomesAFileNameWithoutAccentsOrSigns() {
        assertThat(ActivityPhotos.slug("Paseo en velero por la bahía")).isEqualTo("paseo-en-velero-por-la-bahia");
        assertThat(ActivityPhotos.slug("Bicicleta por la costa esteña")).isEqualTo("bicicleta-por-la-costa-estena");
        assertThat(ActivityPhotos.slug("  ¡Cata de vinos! (2x1)  ")).isEqualTo("cata-de-vinos-2x1");
    }
}
