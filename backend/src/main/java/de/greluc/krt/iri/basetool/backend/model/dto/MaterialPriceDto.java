package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Material Price payload. */
public record MaterialPriceDto(
    UUID id,
    String terminalName,
    BigDecimal priceBuy,
    BigDecimal priceSell,
    Integer scuBuy,
    Integer scuSell,
    Boolean statusBuy,
    Boolean statusSell) {}
