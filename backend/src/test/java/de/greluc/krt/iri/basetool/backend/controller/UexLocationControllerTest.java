package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import de.greluc.krt.iri.basetool.backend.model.dto.UexLocationDto;
import de.greluc.krt.iri.basetool.backend.service.PersonalInventoryItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UexLocationControllerTest {

    @Mock
    private PersonalInventoryItemService service;

    @InjectMocks
    private UexLocationController controller;

    @Test
    void searchShouldUseDefaultLimitWhenAbsent() {
        when(service.searchLocations(any(), any(Integer.class))).thenReturn(List.of());

        controller.search("lorville", null);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(service).searchLocations(any(), limitCaptor.capture());
        assertEquals(25, limitCaptor.getValue(), "Default typeahead limit must be 25.");
    }

    @Test
    void searchShouldClampOversizedLimit() {
        when(service.searchLocations(any(), any(Integer.class))).thenReturn(List.of());

        controller.search(null, 9999);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(service).searchLocations(any(), limitCaptor.capture());
        assertEquals(2000, limitCaptor.getValue(), "Limit must be clamped to the configured max (2000).");
    }

    @Test
    void searchShouldClampNonPositiveLimitToOne() {
        when(service.searchLocations(any(), any(Integer.class))).thenReturn(List.of());

        controller.search(null, 0);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(service).searchLocations(any(), limitCaptor.capture());
        assertEquals(1, limitCaptor.getValue());
    }

    @Test
    void searchShouldReturnServiceResultUnchanged() {
        UexLocationDto hit = new UexLocationDto(1, PersonalInventoryLocationType.CITY,
                "Lorville", "Stanton", "Hurston");
        when(service.searchLocations(any(), any(Integer.class))).thenReturn(List.of(hit));

        List<UexLocationDto> result = controller.search("lor", 25);

        assertEquals(1, result.size());
        assertEquals("Lorville", result.get(0).name());
    }
}
