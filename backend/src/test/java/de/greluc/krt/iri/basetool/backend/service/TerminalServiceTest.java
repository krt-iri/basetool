package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TerminalService}'s admin-override mutators. The visibility flip is already
 * covered indirectly by {@code TerminalControllerTest}; these tests pin the more delicate contract
 * that "set" mutators write both the value and the {@code *Overridden} flag, and that "clear"
 * mutators only flip the flag back without touching the value column.
 */
@ExtendWith(MockitoExtension.class)
class TerminalServiceTest {

  @Mock private TerminalRepository terminalRepository;

  @InjectMocks private TerminalService service;

  @Test
  void setLoadingDockOverride_storesValueAndFlag() {
    UUID id = UUID.randomUUID();
    Terminal terminal = new Terminal();
    terminal.setId(id);
    when(terminalRepository.findById(id)).thenReturn(Optional.of(terminal));
    when(terminalRepository.save(any(Terminal.class))).thenAnswer(i -> i.getArgument(0));

    service.setLoadingDockOverride(id, true);

    ArgumentCaptor<Terminal> cap = ArgumentCaptor.forClass(Terminal.class);
    verify(terminalRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock());
    assertTrue(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void clearLoadingDockOverride_flipsFlagButLeavesValueAlone() {
    UUID id = UUID.randomUUID();
    Terminal terminal = new Terminal();
    terminal.setId(id);
    terminal.setHasLoadingDock(true);
    terminal.setHasLoadingDockOverridden(true);
    when(terminalRepository.findById(id)).thenReturn(Optional.of(terminal));
    when(terminalRepository.save(any(Terminal.class))).thenAnswer(i -> i.getArgument(0));

    service.clearLoadingDockOverride(id);

    ArgumentCaptor<Terminal> cap = ArgumentCaptor.forClass(Terminal.class);
    verify(terminalRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock(), "value column must not be touched on clear");
    assertFalse(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void setAutoLoadOverride_storesValueAndFlag() {
    UUID id = UUID.randomUUID();
    Terminal terminal = new Terminal();
    terminal.setId(id);
    when(terminalRepository.findById(id)).thenReturn(Optional.of(terminal));
    when(terminalRepository.save(any(Terminal.class))).thenAnswer(i -> i.getArgument(0));

    service.setAutoLoadOverride(id, false);

    ArgumentCaptor<Terminal> cap = ArgumentCaptor.forClass(Terminal.class);
    verify(terminalRepository).save(cap.capture());
    assertFalse(cap.getValue().getIsAutoLoad());
    assertTrue(cap.getValue().getIsAutoLoadOverridden());
  }

  @Test
  void clearAutoLoadOverride_flipsFlagButLeavesValueAlone() {
    UUID id = UUID.randomUUID();
    Terminal terminal = new Terminal();
    terminal.setId(id);
    terminal.setIsAutoLoad(true);
    terminal.setIsAutoLoadOverridden(true);
    when(terminalRepository.findById(id)).thenReturn(Optional.of(terminal));
    when(terminalRepository.save(any(Terminal.class))).thenAnswer(i -> i.getArgument(0));

    service.clearAutoLoadOverride(id);

    ArgumentCaptor<Terminal> cap = ArgumentCaptor.forClass(Terminal.class);
    verify(terminalRepository).save(cap.capture());
    assertTrue(cap.getValue().getIsAutoLoad(), "value column must not be touched on clear");
    assertFalse(cap.getValue().getIsAutoLoadOverridden());
  }
}
