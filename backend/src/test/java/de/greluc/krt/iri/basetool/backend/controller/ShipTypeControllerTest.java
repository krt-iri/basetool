package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.ShipMapper;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.backend.service.ShipTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure-method unit tests for {@link ShipTypeController}. Ship-types are
 * UEX-imported (see {@code UexVehicleService}); only the {@code hidden} flag
 * is mutable here. The list endpoint reuses the {@link ShipMapper} that the
 * ShipMapperTest already covers — here we only verify delegation.
 */
@ExtendWith(MockitoExtension.class)
class ShipTypeControllerTest {

    @Mock private ShipTypeService service;
    @Mock private ShipMapper mapper;

    @InjectMocks private ShipTypeController controller;

    @Test
    void getAll_passesIncludeHiddenToService() {
        when(service.getAllShipTypes(any(Pageable.class), eq(true)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getAllShipTypes(null, null, null, true);

        verify(service).getAllShipTypes(any(Pageable.class), eq(true));
    }

    @Test
    void getAll_wrapsServicePageIntoPageResponse_usingShipTypeMapper() {
        ShipType entity = new ShipType();
        ShipTypeDto dto = new ShipTypeDto(UUID.randomUUID(), "Cutlass Black", null, null, 46, false);
        when(service.getAllShipTypes(any(Pageable.class), eq(false))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.shipTypeToDto(entity)).thenReturn(dto);

        PageResponse<ShipTypeDto> resp = controller.getAllShipTypes(null, null, null, false);

        assertEquals(1, resp.totalElements());
        assertSame(dto, resp.content().getFirst());
        // Note: the list endpoint uses `shipTypeToDto`, NOT `toDto`. A regression
        // could accidentally serialise as the bigger ShipDto.
        verify(mapper).shipTypeToDto(entity);
    }

    @Test
    void getById_delegatesAndMaps() {
        UUID id = UUID.randomUUID();
        ShipType entity = new ShipType();
        ShipTypeDto dto = new ShipTypeDto(id, "x", null, null, 1, false);
        when(service.getShipType(id)).thenReturn(entity);
        when(mapper.shipTypeToDto(entity)).thenReturn(dto);

        ShipTypeDto result = controller.getShipType(id);

        assertSame(dto, result);
    }

    @Test
    void updateVisibility_passesHiddenFlagVerbatim() {
        UUID id = UUID.randomUUID();
        ShipType updated = new ShipType();
        ShipTypeDto dto = new ShipTypeDto(id, "x", null, null, 1, true);

        when(service.updateShipTypeVisibility(id, true)).thenReturn(updated);
        when(mapper.shipTypeToDto(updated)).thenReturn(dto);

        ShipTypeDto result = controller.updateShipTypeVisibility(id, true);

        assertSame(dto, result);
        verify(service).updateShipTypeVisibility(id, true);
    }

    @Test
    void updateVisibility_falsePathForwardsFalse() {
        UUID id = UUID.randomUUID();
        when(service.updateShipTypeVisibility(id, false)).thenReturn(new ShipType());
        when(mapper.shipTypeToDto(any())).thenReturn(new ShipTypeDto(id, "x", null, null, 1, false));

        controller.updateShipTypeVisibility(id, false);

        verify(service).updateShipTypeVisibility(id, false);
    }
}
