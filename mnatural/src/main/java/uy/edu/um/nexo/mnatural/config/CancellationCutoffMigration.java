package uy.edu.um.nexo.mnatural.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// El plazo para cancelar pasó de horas a minutos. ddl-auto=update crea la columna nueva vacía, y vacío
// significa "se puede cancelar hasta que empiece", así que sin esto cada actividad guardada perdería su plazo.
// Convierte los valores y borra la columna vieja una sola vez: si ya no está, no hace nada.
// TODO: sacarla cuando todas las bases (las de cada integrante y la de la entrega) ya estén convertidas
@Component
@Order(0)
public class CancellationCutoffMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CancellationCutoffMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public CancellationCutoffMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!columnExists("cancellation_cutoff_hours") || !columnExists("cancellation_cutoff_minutes")) {
            return;
        }
        int converted = jdbcTemplate.update("""
                update activity set cancellation_cutoff_minutes = cancellation_cutoff_hours * 60
                where cancellation_cutoff_hours is not null and cancellation_cutoff_minutes is null
                """);
        jdbcTemplate.execute("alter table activity drop column cancellation_cutoff_hours");
        log.info("Plazo de cancelación pasado de horas a minutos en {} actividades", converted);
    }

    private boolean columnExists(String column) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = current_schema() and table_name = 'activity' and column_name = ?
                """, Integer.class, column);
        return count != null && count > 0;
    }
}
