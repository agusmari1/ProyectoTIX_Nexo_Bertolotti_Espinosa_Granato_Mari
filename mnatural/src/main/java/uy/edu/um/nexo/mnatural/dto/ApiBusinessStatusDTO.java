package uy.edu.um.nexo.mnatural.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Verificación de un emprendimiento: si existe y si su prestador está aprobado.
// El nombre solo se muestra si está aprobado, y los campos nulos no aparecen en el JSON
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiBusinessStatusDTO(boolean exists, boolean providerApproved, String name) {

    public static ApiBusinessStatusDTO notFound() {
        return new ApiBusinessStatusDTO(false, false, null);
    }

    public static ApiBusinessStatusDTO notApproved() {
        return new ApiBusinessStatusDTO(true, false, null);
    }

    public static ApiBusinessStatusDTO approved(String name) {
        return new ApiBusinessStatusDTO(true, true, name);
    }
}
