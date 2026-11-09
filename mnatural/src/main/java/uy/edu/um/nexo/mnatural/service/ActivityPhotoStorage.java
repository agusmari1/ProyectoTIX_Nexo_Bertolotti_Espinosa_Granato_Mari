package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

// Valida y guarda en disco las fotos que suben los prestadores (app.uploads.dir/actividades).
// El nombre y el tipo que manda el navegador no se usan: se puede escribir cualquier cosa ahí. El tipo sale de los
// primeros bytes del archivo y el nombre se genera acá, así nadie puede pisar otro archivo ni subir algo que no sea
// una imagen
@Service
public class ActivityPhotoStorage {

    static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final String FIELD = "photo";
    private static final int HEADER_BYTES = 12;

    private static final Logger log = LoggerFactory.getLogger(ActivityPhotoStorage.class);

    private final Path folder;

    public ActivityPhotoStorage(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.folder = Path.of(uploadsDir, "actividades").toAbsolutePath().normalize();
    }

    // Devuelve el nombre con el que quedó guardada, que es lo que se guarda en la actividad
    public String store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new ActivityValidationException(FIELD, "Subí una foto de la actividad.");
        }
        if (photo.getSize() > MAX_BYTES) {
            throw new ActivityValidationException(FIELD, "La foto no puede superar los 5 MB.");
        }
        try {
            String extension = detectExtension(photo).orElseThrow(() -> new ActivityValidationException(FIELD,
                    "La foto tiene que ser una imagen JPG, PNG o WEBP."));
            String fileName = UUID.randomUUID() + "." + extension;
            Files.createDirectories(folder);
            try (InputStream content = photo.getInputStream()) {
                Files.copy(content, folder.resolve(fileName));
            }
            return fileName;
        } catch (IOException e) {
            log.error("No se pudo guardar una foto de actividad", e);
            throw new ActivityValidationException(FIELD, "No se pudo guardar la foto. Probá de nuevo.");
        }
    }

    // Borra una foto reemplazada recién cuando el cambio quedó guardado en la base: si la transacción falla,
    // la actividad sigue usando esa foto y tiene que seguir existiendo
    public void deleteAfterCommit(String fileName) {
        if (fileName == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(fileName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(fileName);
            }
        });
    }

    private void delete(String fileName) {
        try {
            Files.deleteIfExists(folder.resolve(fileName));
        } catch (IOException e) {
            // Un archivo viejo que quedó en el disco no afecta a nadie: no se le muestra un error al prestador
            log.warn("No se pudo borrar la foto {}", fileName, e);
        }
    }

    private Optional<String> detectExtension(MultipartFile photo) throws IOException {
        byte[] header;
        try (InputStream content = photo.getInputStream()) {
            header = content.readNBytes(HEADER_BYTES);
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("jpg");
        }
        if (startsWith(header, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("png");
        }
        if (header.length == HEADER_BYTES && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 12).equals("WEBP")) {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    private boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String ascii(byte[] header, int from, int to) {
        return new String(Arrays.copyOfRange(header, from, to), StandardCharsets.US_ASCII);
    }
}
