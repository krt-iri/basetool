package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MaterialSellingTerminalDto(
    UUID terminalId, String terminalName, BigDecimal priceSell) {}
