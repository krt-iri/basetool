package de.greluc.krt.iri.basetool.frontend.model.form;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class JobOrderHandoverForm {
    private String handoverTime; // We will parse it to Instant before sending
    private String recipientHandle;
    private String recipientSquadron;
    private List<JobOrderHandoverItemForm> items = new ArrayList<>();

    @Data
    public static class JobOrderHandoverItemForm {
        private UUID inventoryItemId;
        private Double amount;
    }
}
