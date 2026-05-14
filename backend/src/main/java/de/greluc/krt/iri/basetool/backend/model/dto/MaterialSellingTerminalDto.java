package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Material Selling Terminal payload. */
public record MaterialSellingTerminalDto(
    UUID terminalId, String terminalName, BigDecimal priceSell) {}
