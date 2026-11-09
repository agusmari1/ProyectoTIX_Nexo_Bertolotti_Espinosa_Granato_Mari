package uy.edu.um.nexo.mnatural.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

// Muestra las fotos subidas por los prestadores, que están fuera del proyecto compilado (app.uploads.dir).
// La dirección cuelga de /img/**, que SecurityConfig ya deja pública para que se vean sin iniciar sesión
@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    private final String uploadsLocation;

    public UploadsWebConfig(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        // Tiene que terminar en barra: si no, Spring lo toma como un archivo y no como una carpeta
        this.uploadsLocation = Path.of(uploadsDir).toAbsolutePath().normalize().toUri().toString().replaceAll("/?$", "/");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/img/subidas/**").addResourceLocations(uploadsLocation);
    }
}
