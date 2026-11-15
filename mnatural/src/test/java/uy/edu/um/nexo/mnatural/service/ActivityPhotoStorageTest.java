package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Guarda en una carpeta temporal que JUnit borra al terminar
class ActivityPhotoStorageTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final byte[] WEBP = "RIFFxxxxWEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDir;

    private ActivityPhotoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ActivityPhotoStorage(tempDir.toString());
    }

    private MockMultipartFile photo(String name, byte[] content) {
        return new MockMultipartFile("photo", name, "image/jpeg", content);
    }

    private Path stored(String fileName) {
        return tempDir.resolve("actividades").resolve(fileName);
    }

    @Test
    void savesEachAllowedFormatWithTheExtensionOfItsRealContent() throws Exception {
        String jpg = storage.store(photo("foto.jpeg", JPG));
        String png = storage.store(photo("foto.png", PNG));
        String webp = storage.store(photo("foto.webp", WEBP));

        assertThat(jpg).endsWith(".jpg");
        assertThat(png).endsWith(".png");
        assertThat(webp).endsWith(".webp");
        assertThat(Files.readAllBytes(stored(jpg))).isEqualTo(JPG);
        assertThat(stored(png)).exists();
        assertThat(stored(webp)).exists();
    }

    @Test
    void theNameSentByTheBrowserIsNeverUsed() {
        String first = storage.store(photo("../../peligroso.jpg", JPG));
        String second = storage.store(photo("../../peligroso.jpg", JPG));

        assertThat(first).doesNotContain("peligroso").doesNotContain("..");
        assertThat(second).isNotEqualTo(first);
        assertThat(tempDir.getParent().resolve("peligroso.jpg")).doesNotExist();
    }

    @Test
    void aFileThatIsNotReallyAnImageIsRejectedEvenWithAnImageName() {
        assertRejected(photo("virus.jpg", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)),
                "JPG, PNG o WEBP");
        assertRejected(photo("dibujo.svg", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8)),
                "JPG, PNG o WEBP");
        assertRejected(photo("corta.jpg", new byte[]{(byte) 0xFF}), "JPG, PNG o WEBP");
    }

    @Test
    void aMissingOrEmptyPhotoIsRejected() {
        assertRejected(null, "Subí una foto");
        assertRejected(photo("vacia.jpg", new byte[0]), "Subí una foto");
    }

    @Test
    void thePhotoCanWeighUpToTheLimitButNotMore() {
        byte[] atTheLimit = Arrays.copyOf(JPG, (int) ActivityPhotoStorage.MAX_BYTES);
        byte[] overTheLimit = Arrays.copyOf(JPG, (int) ActivityPhotoStorage.MAX_BYTES + 1);

        assertThat(storage.store(photo("justa.jpg", atTheLimit))).endsWith(".jpg");
        assertRejected(photo("pesada.jpg", overTheLimit), "5 MB");
    }

    @Test
    void deletesAReplacedPhotoAndIgnoresOneThatIsNotThere() {
        String fileName = storage.store(photo("foto.jpg", JPG));

        storage.deleteAfterCommit(fileName);
        storage.deleteAfterCommit("ya-no-esta.jpg");
        storage.deleteAfterCommit(null);

        assertThat(stored(fileName)).doesNotExist();
    }

    private void assertRejected(MockMultipartFile file, String message) {
        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOfSatisfying(ActivityValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("photo"))
                .hasMessageContaining(message);
    }
}
