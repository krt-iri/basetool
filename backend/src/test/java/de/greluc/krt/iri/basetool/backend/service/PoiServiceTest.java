package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Poi;
import de.greluc.krt.iri.basetool.backend.repository.PoiRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link PoiService}'s admin-override mutators. */
@ExtendWith(MockitoExtension.class)
class PoiServiceTest {

  @Mock private PoiRepository poiRepository;

  @InjectMocks private PoiService service;

  @Test
  void setLoadingDockOverride_storesValueAndFlag() {
    UUID id = UUID.randomUUID();
    Poi poi = new Poi();
    poi.setId(id);
    when(poiRepository.findById(id)).thenReturn(Optional.of(poi));
    when(poiRepository.save(any(Poi.class))).thenAnswer(i -> i.getArgument(0));

    service.setLoadingDockOverride(id, true);

    ArgumentCaptor<Poi> cap = ArgumentCaptor.forClass(Poi.class);
    verify(poiRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock());
    assertTrue(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void clearLoadingDockOverride_flipsFlagButLeavesValueAlone() {
    UUID id = UUID.randomUUID();
    Poi poi = new Poi();
    poi.setId(id);
    poi.setHasLoadingDock(true);
    poi.setHasLoadingDockOverridden(true);
    when(poiRepository.findById(id)).thenReturn(Optional.of(poi));
    when(poiRepository.save(any(Poi.class))).thenAnswer(i -> i.getArgument(0));

    service.clearLoadingDockOverride(id);

    ArgumentCaptor<Poi> cap = ArgumentCaptor.forClass(Poi.class);
    verify(poiRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock(), "value column must not be touched on clear");
    assertFalse(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void getPoi_throwsNotFoundOnMissingId() {
    UUID id = UUID.randomUUID();
    when(poiRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.getPoi(id));
  }
}
