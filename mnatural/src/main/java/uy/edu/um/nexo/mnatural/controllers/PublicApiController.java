package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.nexo.mnatural.dto.ApiActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ApiAvailableActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ApiBusinessDTO;
import uy.edu.um.nexo.mnatural.dto.ApiBusinessStatusDTO;
import uy.edu.um.nexo.mnatural.dto.ApiTopActivityDTO;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.service.PublicApiService;

import java.time.LocalDate;
import java.util.List;

// API pública (ESPECIFICACION.md, sección 9), documentada en API.md. Responde JSON y no pide
// sesión: GET /api/** está en la lista pública de SecurityConfig
@RestController
@RequestMapping("/api")
public class PublicApiController {

    private final PublicApiService apiService;

    public PublicApiController(PublicApiService apiService) {
        this.apiService = apiService;
    }

    @GetMapping("/businesses")
    public List<ApiBusinessDTO> businesses() {
        return apiService.findBusinesses();
    }

    @GetMapping("/businesses/{id}/status")
    public ApiBusinessStatusDTO businessStatus(@PathVariable Long id) {
        return apiService.businessStatus(id);
    }

    @GetMapping("/activities/available")
    public List<ApiAvailableActivityDTO> availableActivities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) String locality) {
        return apiService.findAvailable(date, department, locality);
    }

    @GetMapping("/activities/top")
    public List<ApiTopActivityDTO> topActivities(@RequestParam Department department) {
        return apiService.findTopOfMonth(department);
    }

    @GetMapping("/activities/search")
    public List<ApiActivityDTO> searchActivities(@RequestParam String q) {
        return apiService.search(q);
    }
}
