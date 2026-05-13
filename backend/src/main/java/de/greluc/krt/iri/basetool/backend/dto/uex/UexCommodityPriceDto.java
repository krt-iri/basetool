package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record UexCommodityPriceDto(
    @JsonProperty("id_commodity") Integer idCommodity,
    @JsonProperty("commodity_name") String commodityName,
    @JsonProperty("id_terminal") Integer idTerminal,
    @JsonProperty("terminal_name") String terminalName,
    @JsonProperty("price_buy") BigDecimal priceBuy,
    @JsonProperty("price_sell") BigDecimal priceSell,
    @JsonProperty("scu_buy") Integer scuBuy,
    @JsonProperty("scu_sell") Integer scuSell,
    @JsonProperty("scu_sell_stock") Integer scuSellStock,
    @JsonProperty("status_buy") Integer statusBuy,
    @JsonProperty("status_sell") Integer statusSell,
    @JsonProperty("date_modified") Long dateModified) {
  public Instant getParsedDateModified() {
    return dateModified != null ? Instant.ofEpochSecond(dateModified) : null;
  }

  public Boolean isStatusBuy() {
    return statusBuy != null && statusBuy == 1;
  }

  public Boolean isStatusSell() {
    return statusSell != null && statusSell == 1;
  }
}
