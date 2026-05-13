package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexRefineryYieldDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexRefiningMethodDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.RefineryYield;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryYieldRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UexRefinerySyncService {

  private final UexClient uexClient;
  private final RefiningMethodRepository refiningMethodRepository;
  private final RefineryYieldRepository refineryYieldRepository;
  private final MaterialRepository materialRepository;
  private final TerminalRepository terminalRepository;

  @Transactional
  public void syncRefiningMethods() {
    log.info("Starting sync for Refining Methods...");
    List<UexRefiningMethodDto> dtos = uexClient.getRefineriesMethods();
    if (dtos.isEmpty()) {
      return;
    }

    int added = 0;
    int updated = 0;

    for (UexRefiningMethodDto dto : dtos) {
      if (dto.name() == null || dto.name().isBlank()) {
        continue;
      }

      RefiningMethod entity =
          refiningMethodRepository
              .findByName(dto.name())
              .orElseGet(
                  () -> {
                    RefiningMethod n = new RefiningMethod();
                    n.setName(dto.name());
                    return n;
                  });

      boolean isNew = entity.getId() == null;

      entity.setCode(dto.code());
      entity.setRatingYield(dto.ratingYield());
      entity.setRatingCost(dto.ratingCost());
      entity.setRatingSpeed(dto.ratingSpeed());

      refiningMethodRepository.save(entity);

      if (isNew) {
        added++;
      } else {
        updated++;
      }
    }
    log.info("Finished UEX Refining Methods sync: {} added, {} updated", added, updated);
  }

  @Transactional
  public void syncRefineryYields() {
    log.info("Starting sync for Refinery Yields...");
    List<UexRefineryYieldDto> dtos = uexClient.getRefineriesYields();
    if (dtos.isEmpty()) {
      return;
    }

    int processed = 0;

    for (UexRefineryYieldDto dto : dtos) {
      if (dto.idCommodity() == null || dto.idTerminal() == null || dto.value() == null) {
        continue;
      }

      Material material = materialRepository.findByIdCommodity(dto.idCommodity()).orElse(null);
      Terminal terminal = terminalRepository.findByIdTerminal(dto.idTerminal()).orElse(null);

      if (material == null || terminal == null) {
        continue;
      }

      RefineryYield entity =
          refineryYieldRepository
              .findByTerminalIdAndMaterialId(terminal.getId(), material.getId())
              .orElseGet(
                  () -> {
                    RefineryYield n = new RefineryYield();
                    n.setTerminal(terminal);
                    n.setMaterial(material);
                    return n;
                  });

      entity.setYieldBonus(dto.value());
      refineryYieldRepository.save(entity);
      processed++;
    }
    log.info("Finished UEX Refinery Yields sync: Processed {} yields", processed);
  }
}
