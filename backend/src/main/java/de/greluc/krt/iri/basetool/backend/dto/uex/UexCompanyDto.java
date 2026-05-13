package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

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
