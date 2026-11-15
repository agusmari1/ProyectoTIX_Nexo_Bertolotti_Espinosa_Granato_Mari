package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancellationCutoffMigrationTest {

    private JdbcTemplate jdbcTemplate;
    private CancellationCutoffMigration migration;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        migration = new CancellationCutoffMigration(jdbcTemplate);
    }

    @Test
    void convertsHoursToMinutesAndDropsTheOldColumn() {
        givenColumn("cancellation_cutoff_hours", 1);
        givenColumn("cancellation_cutoff_minutes", 1);

        migration.run();

        ArgumentCaptor<String> update = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(update.capture());
        assertThat(update.getValue()).contains("cancellation_cutoff_minutes = cancellation_cutoff_hours * 60");
        verify(jdbcTemplate).execute("alter table activity drop column cancellation_cutoff_hours");
    }

    @Test
    void doesNothingIfItWasAlreadyConverted() {
        givenColumn("cancellation_cutoff_hours", 0);
        givenColumn("cancellation_cutoff_minutes", 1);

        migration.run();

        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void doesNothingIfTheNewColumnDoesNotExistYet() {
        givenColumn("cancellation_cutoff_hours", 1);
        givenColumn("cancellation_cutoff_minutes", 0);

        migration.run();

        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    private void givenColumn(String column, int count) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(column))).thenReturn(count);
    }
}
