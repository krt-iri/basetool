package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import de.greluc.krt.iri.basetool.backend.repository.SpaceStationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link SpaceStationService}'s admin-override mutators. */
@ExtendWith(MockitoExtension.class)
class SpaceStationServiceTest {

  @Mock private SpaceStationRepository spaceStationRepository;

  @InjectMocks private SpaceStationService service;

  @Test
  void setLoadingDockOverride_storesValueAndFlag() {
    UUID id = UUID.randomUUID();
    SpaceStation station = new SpaceStation();
    station.setId(id);
    when(spaceStationRepository.findById(id)).thenReturn(Optional.of(station));
    when(spaceStationRepository.save(any(SpaceStation.class))).thenAnswer(i -> i.getArgument(0));

    service.setLoadingDockOverride(id, true);

    ArgumentCaptor<SpaceStation> cap = ArgumentCaptor.forClass(SpaceStation.class);
    verify(spaceStationRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock());
    assertTrue(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void clearLoadingDockOverride_flipsFlagButLeavesValueAlone() {
    UUID id = UUID.randomUUID();
    SpaceStation station = new SpaceStation();
    station.setId(id);
    station.setHasLoadingDock(true);
    station.setHasLoadingDockOverridden(true);
    when(spaceStationRepository.findById(id)).thenReturn(Optional.of(station));
    when(spaceStationRepository.save(any(SpaceStation.class))).thenAnswer(i -> i.getArgument(0));

    service.clearLoadingDockOverride(id);

    ArgumentCaptor<SpaceStation> cap = ArgumentCaptor.forClass(SpaceStation.class);
    verify(spaceStationRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock(), "value column must not be touched on clear");
    assertFalse(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void getSpaceStation_throwsNotFoundOnMissingId() {
    UUID id = UUID.randomUUID();
    when(spaceStationRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.getSpaceStation(id));
  }
}
