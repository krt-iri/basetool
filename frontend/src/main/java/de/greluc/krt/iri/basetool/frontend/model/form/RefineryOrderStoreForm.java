package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RefineryOrderStoreForm {
    @NotEmpty
    @Valid
    private List<RefineryOrderStoreItemForm> items = new ArrayList<>();
}
