package uy.edu.um.nexo.mnatural.dto;

// Datos de contacto del emprendimiento, que el turista ve solo con la reserva confirmada
// (ESPECIFICACION.md, sección 4)
public record BusinessContactDTO(String name, String phoneNumber, String email, String address) {
}
