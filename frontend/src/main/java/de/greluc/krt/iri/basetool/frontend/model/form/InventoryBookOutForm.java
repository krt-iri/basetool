package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;
import de.greluc.krt.iri.basetool.frontend.model.dto.CheckoutType;

@Data
public class InventoryBookOutForm {

    @NotNull
    @Min(0)
    private Double amount;

    private Double targetAmount;

    private Double maxAmount;

    private UUID targetUserId;

    private UUID targetLocationId;

    private CheckoutType type;

    private String terminal;

    @Min(0)
    private BigDecimal sellAmount;

    private Boolean isGlobal;

    private Long version;
}
