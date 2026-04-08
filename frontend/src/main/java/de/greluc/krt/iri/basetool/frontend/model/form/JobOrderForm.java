package de.greluc.krt.iri.basetool.frontend.model.form;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class JobOrderForm {
    private String squadron;
    private String handle;
    private Long version;
    private List<JobOrderMaterialForm> materials = new ArrayList<>();

    public JobOrderForm() {
        materials.add(new JobOrderMaterialForm());
    }

    @Data
    public static class JobOrderMaterialForm {
        private UUID materialId;
        private Integer minQuality = 100;
        private Double amount;
    }
}
