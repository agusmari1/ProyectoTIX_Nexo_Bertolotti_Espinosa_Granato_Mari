package uy.edu.um.nexo.mnatural.weather;

import uy.edu.um.nexo.mnatural.entities.Department;

import java.util.EnumMap;
import java.util.Map;

// Coordenadas (latitud, longitud) de la capital de cada departamento. El pronóstico de una actividad
// se pide para la capital de su departamento, así no hace falta ningún servicio para buscar la localidad
public final class DepartmentCoordinates {

    public record Coordinates(double latitude, double longitude) {
    }

    private static final Map<Department, Coordinates> CAPITALS = new EnumMap<>(Department.class);

    static {
        CAPITALS.put(Department.ARTIGAS, new Coordinates(-30.4000, -56.4667));
        CAPITALS.put(Department.CANELONES, new Coordinates(-34.5228, -56.2778));
        CAPITALS.put(Department.CERRO_LARGO, new Coordinates(-32.3667, -54.1667));         // Melo
        CAPITALS.put(Department.COLONIA, new Coordinates(-34.4626, -57.8400));             // Colonia del Sacramento
        CAPITALS.put(Department.DURAZNO, new Coordinates(-33.3806, -56.5236));
        CAPITALS.put(Department.FLORES, new Coordinates(-33.5167, -56.9000));              // Trinidad
        CAPITALS.put(Department.FLORIDA, new Coordinates(-34.0956, -56.2142));
        CAPITALS.put(Department.LAVALLEJA, new Coordinates(-34.3759, -55.2377));           // Minas
        CAPITALS.put(Department.MALDONADO, new Coordinates(-34.9000, -54.9500));
        CAPITALS.put(Department.MONTEVIDEO, new Coordinates(-34.9011, -56.1645));
        CAPITALS.put(Department.PAYSANDU, new Coordinates(-32.3214, -58.0756));
        CAPITALS.put(Department.RIO_NEGRO, new Coordinates(-33.1167, -58.3000));           // Fray Bentos
        CAPITALS.put(Department.RIVERA, new Coordinates(-30.9053, -55.5508));
        CAPITALS.put(Department.ROCHA, new Coordinates(-34.4833, -54.3333));
        CAPITALS.put(Department.SALTO, new Coordinates(-31.3833, -57.9667));
        CAPITALS.put(Department.SAN_JOSE, new Coordinates(-34.3375, -56.7136));            // San José de Mayo
        CAPITALS.put(Department.SORIANO, new Coordinates(-33.2524, -58.0305));             // Mercedes
        CAPITALS.put(Department.TACUAREMBO, new Coordinates(-31.7167, -55.9833));
        CAPITALS.put(Department.TREINTA_Y_TRES, new Coordinates(-33.2333, -54.3833));
    }

    private DepartmentCoordinates() {
    }

    public static Coordinates of(Department department) {
        return CAPITALS.get(department);
    }
}
