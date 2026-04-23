package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Form-Backing-Object fuer eine einzelne Materialzeile des Einlagern-Dialogs
 * eines Raffinerieauftrags.
 *
 * <p>Die Mengenvalidierung folgt der projektweiten Konvention fuer Mengen-Eingabefelder:
 * Dezimalzahl, &gt;= 0, max. 3 Nachkommastellen. Die Notiz ist optional und wird beim
 * Einlagern am erzeugten {@code InventoryItem} hinterlegt.
 */
@Data
public class RefineryOrderStoreItemForm {
    
    @NotNull
    private UUID materialId;
    
    private String materialName;
    
    private String quantityType;
    
    @NotNull
    private UUID locationId;
    
    @NotNull
    @Min(0)
    @Max(1000)
    private Integer quality;
    
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 15, fraction = 3)
    private Double amount;
    
    private Boolean amountFixed;
    
    private UUID userId;
    
    private UUID jobOrderId;

    @Size(max = 1000)
    private String note;
}
