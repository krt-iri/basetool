package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Inbound JSON record for UEX Corp's <code>/companies</code> endpoint. Mapped to the project's own
 * {@code Manufacturer} entity by {@code UexVehicleService}; downstream code consumes the entity,
 * not this DTO.
 */
@Builder
public record UexCompanyDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("wiki") String wiki,
    @JsonProperty("industry") String industry,
    @JsonProperty("is_item_manufacturer") Integer isItemManufacturer,
    @JsonProperty("is_vehicle_manufacturer") Integer isVehicleManufacturer,
    @JsonProperty("date_added") Long dateAdded,
    @JsonProperty("date_modified") Long dateModified) {
  public Boolean isVehicleManufacturerFlag() {
    return isVehicleManufacturer != null && isVehicleManufacturer == 1;
  }
}
