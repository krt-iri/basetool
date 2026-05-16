package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Outpost;
import de.greluc.krt.iri.basetool.backend.repository.OutpostRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link OutpostService}'s admin-override mutators. */
@ExtendWith(MockitoExtension.class)
class OutpostServiceTest {

  @Mock private OutpostRepository outpostRepository;

  @InjectMocks private OutpostService service;

  @Test
  void setLoadingDockOverride_storesValueAndFlag() {
    UUID id = UUID.randomUUID();
    Outpost outpost = new Outpost();
    outpost.setId(id);
    when(outpostRepository.findById(id)).thenReturn(Optional.of(outpost));
    when(outpostRepository.save(any(Outpost.class))).thenAnswer(i -> i.getArgument(0));

    service.setLoadingDockOverride(id, true);

    ArgumentCaptor<Outpost> cap = ArgumentCaptor.forClass(Outpost.class);
    verify(outpostRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock());
    assertTrue(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void clearLoadingDockOverride_flipsFlagButLeavesValueAlone() {
    UUID id = UUID.randomUUID();
    Outpost outpost = new Outpost();
    outpost.setId(id);
    outpost.setHasLoadingDock(true);
    outpost.setHasLoadingDockOverridden(true);
    when(outpostRepository.findById(id)).thenReturn(Optional.of(outpost));
    when(outpostRepository.save(any(Outpost.class))).thenAnswer(i -> i.getArgument(0));

    service.clearLoadingDockOverride(id);

    ArgumentCaptor<Outpost> cap = ArgumentCaptor.forClass(Outpost.class);
    verify(outpostRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock(), "value column must not be touched on clear");
    assertFalse(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void getOutpost_throwsNotFoundOnMissingId() {
    UUID id = UUID.randomUUID();
    when(outpostRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.getOutpost(id));
  }
}
