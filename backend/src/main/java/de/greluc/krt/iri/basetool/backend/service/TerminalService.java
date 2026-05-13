package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerminalService {

    private final TerminalRepository terminalRepository;

    public Page<Terminal> getAllTerminals(Pageable pageable) {
        return terminalRepository.findAll(pageable);
    }

    public Terminal getTerminal(UUID id) {
        return terminalRepository.findById(id).orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Terminal not found"));
    }

    @Transactional
    public Terminal updateTerminalVisibility(UUID id, boolean hidden) {
        Terminal terminal = getTerminal(id);
        terminal.setHidden(hidden);
        return terminalRepository.save(terminal);
    }
}
