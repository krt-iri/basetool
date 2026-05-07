package de.greluc.krt.iri.basetool.frontend.model.form;

import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryLocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Form-backing bean for the personal inventory create/update modal. Mirrors backend
 * validation rules so feedback can be rendered before the request hits the backend.
 */
@Data
public class PersonalInventoryForm {

    /** Optional: only present when editing an existing item. */
    private UUID id;

    @NotBlank(message = "{personalInventory.validation.name.required}")
    @Size(max = 120, message = "{personalInventory.validation.name.tooLong}")
    private String name;

    @Size(max = 2000, message = "{personalInventory.validation.note.tooLong}")
    private String note;

    @NotNull(message = "{personalInventory.validation.location.required}")
    private Integer locationUexId;

    @NotNull(message = "{personalInventory.validation.location.required}")
    private PersonalInventoryLocationType locationType;

    /** Display snapshot used to repopulate the typeahead input on validation re-render. */
    private String locationName;

    @NotNull(message = "{personalInventory.validation.quantity.required}")
    @Min(value = 1, message = "{personalInventory.validation.quantity.min}")
    private Integer quantity;

    /** Optimistic-locking version, populated on update only. */
    private Long version;
}
