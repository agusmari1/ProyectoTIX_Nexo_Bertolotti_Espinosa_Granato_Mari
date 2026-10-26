package uy.edu.um.nexo.mnatural.dto;

import org.springframework.format.annotation.DateTimeFormat;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.math.BigDecimal;
import java.time.LocalDate;

// Filtros del listado público. Todos son opcionales y se pueden combinar;
// llegan como parámetros de la URL (por ejemplo /actividades?department=ROCHA&maxPrice=1000)
public class ActivityFilterDTO {

    private Department department;

    private String locality;

    private Long activityTypeId;

    // Formato que envía <input type="date">, por ejemplo 2026-11-05
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    public ActivityFilterDTO() {
    }

    public boolean isEmpty() {
        return department == null && (locality == null || locality.isBlank()) && activityTypeId == null
                && date == null && minPrice == null && maxPrice == null;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public Long getActivityTypeId() {
        return activityTypeId;
    }

    public void setActivityTypeId(Long activityTypeId) {
        this.activityTypeId = activityTypeId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }
}
