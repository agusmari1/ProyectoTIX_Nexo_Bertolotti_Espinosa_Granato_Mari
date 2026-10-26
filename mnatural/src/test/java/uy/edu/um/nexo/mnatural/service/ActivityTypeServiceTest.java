package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.exception.ActivityTypeException;
import uy.edu.um.nexo.mnatural.exception.ActivityTypeNotFoundException;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: el repositorio es un mock, así que no hace falta la base de datos
class ActivityTypeServiceTest {

    private ActivityTypeRepository repository;
    private ActivityTypeService service;

    @BeforeEach
    void setUp() {
        repository = mock(ActivityTypeRepository.class);
        service = new ActivityTypeService(repository);
        when(repository.saveAndFlush(any(ActivityType.class))).then(returnsFirstArg());
        when(repository.save(any(ActivityType.class))).then(returnsFirstArg());
    }

    private ActivityType type(Long id, String name, boolean enabled) {
        ActivityType type = new ActivityType();
        type.setId(id);
        type.setName(name);
        type.setEnabled(enabled);
        return type;
    }

    private void givenExisting(ActivityType... types) {
        when(repository.findAll()).thenReturn(List.of(types));
        for (ActivityType type : types) {
            when(repository.findById(type.getId())).thenReturn(Optional.of(type));
        }
    }

    @Test
    void createSavesAnEnabledTypeWithTheTrimmedName() {
        when(repository.findAll()).thenReturn(List.of());

        service.create("   Senderismo  ");

        ArgumentCaptor<ActivityType> captor = ArgumentCaptor.forClass(ActivityType.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Senderismo");
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void createRejectsABlankName() {
        when(repository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create("   "))
                .isInstanceOf(ActivityTypeException.class)
                .hasMessageContaining("obligatorio");
        verify(repository, never()).saveAndFlush(any(ActivityType.class));
    }

    @Test
    void createRejectsARepeatedNameIgnoringCaseAndAccents() {
        givenExisting(type(1L, "Gastronomía", true));

        assertThatThrownBy(() -> service.create("  GASTRONOMIA "))
                .isInstanceOf(ActivityTypeException.class)
                .hasMessageContaining("Gastronomía");
        verify(repository, never()).saveAndFlush(any(ActivityType.class));
    }

    @Test
    void renameRejectsTheNameOfAnotherType() {
        givenExisting(type(1L, "Senderismo", true), type(2L, "Aventura", true));

        assertThatThrownBy(() -> service.rename(2L, "senderísmo"))
                .isInstanceOf(ActivityTypeException.class)
                .hasMessageContaining("Senderismo");
        verify(repository, never()).save(any(ActivityType.class));
    }

    @Test
    void renameAllowsChangingOnlyTheCaseOfItsOwnName() {
        givenExisting(type(1L, "Senderismo", true));

        ActivityType renamed = service.rename(1L, "SENDERISMO");

        assertThat(renamed.getName()).isEqualTo("SENDERISMO");
    }

    @Test
    void deactivateSetsEnabledToFalse() {
        ActivityType active = type(1L, "Senderismo", true);
        givenExisting(active);

        ActivityType result = service.setEnabled(1L, false);

        assertThat(result.isEnabled()).isFalse();
        verify(repository).save(active);
    }

    @Test
    void activateSetsEnabledToTrue() {
        givenExisting(type(1L, "Senderismo", false));

        assertThat(service.setEnabled(1L, true).isEnabled()).isTrue();
    }

    @Test
    void deleteIsBlockedWhenActivitiesUseTheType() {
        givenExisting(type(1L, "Senderismo", true));
        when(repository.countActivitiesUsing(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ActivityTypeException.class)
                .hasMessageContaining("usan 2 actividades");
        verify(repository, never()).delete(any(ActivityType.class));
    }

    @Test
    void deleteRemovesATypeNobodyUses() {
        ActivityType unused = type(1L, "Senderismo", true);
        givenExisting(unused);
        when(repository.countActivitiesUsing(1L)).thenReturn(0L);

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();
        verify(repository).delete(unused);
    }

    @Test
    void anUnknownTypeIsReportedAsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEnabled(99L, false)).isInstanceOf(ActivityTypeNotFoundException.class);
    }

    @Test
    void countsActivitiesPerType() {
        when(repository.countActivitiesByType()).thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));

        assertThat(service.countActivitiesByType()).isEqualTo(Map.of(1L, 3L));
    }
}
