package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCollectionEntryDto;
import de.greluc.krt.iri.basetool.backend.service.InventoryItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure-method unit tests for {@link MaterialCollectionController}. The
 * controller is a thin delegation layer; the real sorting / aggregation
 * logic lives in {@code InventoryItemService.getMaterialCollection(...)}
 * which has its own test coverage. Here we just guarantee delegation.
 */
@ExtendWith(MockitoExtension.class)
class MaterialCollectionControllerTest {

    @Mock private InventoryItemService inventoryItemService;

    @InjectMocks private MaterialCollectionController controller;

    @Test
    void getMaterialCollection_delegatesJobOrderIdToService_andReturnsList() {
        UUID jobOrderId = UUID.randomUUID();
        List<MaterialCollectionEntryDto> expected = List.of(
                new MaterialCollectionEntryDto(
                        UUID.randomUUID(), 1L, "alice", UUID.randomUUID(),
                        "Lorville", UUID.randomUUID(), "Gold", 800.0, 5.0, false));
        when(inventoryItemService.getMaterialCollection(jobOrderId)).thenReturn(expected);

        List<MaterialCollectionEntryDto> result = controller.getMaterialCollection(jobOrderId);

        assertSame(expected, result, "controller must return the service's list unmodified");
        verify(inventoryItemService).getMaterialCollection(jobOrderId);
        verifyNoMoreInteractions(inventoryItemService);
    }

    @Test
    void getMaterialCollection_emptyResult_isReturnedAsIs() {
        UUID jobOrderId = UUID.randomUUID();
        when(inventoryItemService.getMaterialCollection(jobOrderId)).thenReturn(List.of());

        List<MaterialCollectionEntryDto> result = controller.getMaterialCollection(jobOrderId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
