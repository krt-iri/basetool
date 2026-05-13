package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MaterialPriceDto(
    UUID id,
    String terminalName,
    BigDecimal priceBuy,
    BigDecimal priceSell,
    Integer scuBuy,
    Integer scuSell,
    Boolean statusBuy,
    Boolean statusSell) {}
