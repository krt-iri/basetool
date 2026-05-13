package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RefineryOrderStoreForm {
  @NotEmpty @Valid private List<RefineryOrderStoreItemForm> items = new ArrayList<>();
}
