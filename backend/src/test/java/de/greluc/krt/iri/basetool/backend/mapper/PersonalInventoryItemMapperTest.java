package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryItem;
import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersonalInventoryItemMapperTest {

    private final PersonalInventoryItemMapper mapper = Mappers.getMapper(PersonalInventoryItemMapper.class);

    @Test
    void shouldMapEntityToResponseAndExposeSnapshotAsLocationName() {
        // Given
        UUID id = UUID.randomUUID();
        PersonalInventoryItem entity = PersonalInventoryItem.builder()
                .id(id)
                .ownerSub("user-sub-123")
                .name("Medkit")
                .note("First aid")
                .locationUexId(42)
                .locationType(PersonalInventoryLocationType.CITY)
                .locationNameSnapshot("Lorville")
                .quantity(3)
                .build();
        entity.setVersion(7L);
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        // When
        PersonalInventoryItemResponse response = mapper.toResponse(entity);

        // Then
        assertNotNull(response);
        assertEquals(id, response.id());
        assertEquals("Medkit", response.name());
        assertEquals("First aid", response.note());
        assertEquals(42, response.locationUexId());
        assertEquals(PersonalInventoryLocationType.CITY, response.locationType());
        assertEquals("Lorville", response.locationName(),
                "locationNameSnapshot must be exposed under the simpler 'locationName' DTO field");
        assertEquals(3, response.quantity());
        assertEquals(7L, response.version());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void toEntityShouldNotPopulateOwnerOrSnapshot() {
        // Given
        PersonalInventoryItemCreateRequest req = new PersonalInventoryItemCreateRequest(
                "Ammo", null, 10, PersonalInventoryLocationType.SPACE_STATION, 200);

        // When
        PersonalInventoryItem entity = mapper.toEntity(req);

        // Then – owner sub and snapshot must be set explicitly by the service, not by the mapper
        assertNotNull(entity);
        assertEquals("Ammo", entity.getName());
        assertEquals(PersonalInventoryLocationType.SPACE_STATION, entity.getLocationType());
        assertEquals(10, entity.getLocationUexId());
        assertEquals(200, entity.getQuantity());
        assertNull(entity.getOwnerSub(), "ownerSub must not be derived from the request DTO");
        assertNull(entity.getLocationNameSnapshot(), "snapshot must be set by the service after UEX lookup");
        assertNull(entity.getId());
    }

    @Test
    void updateEntityShouldPreserveOwnerVersionAndSnapshot() {
        // Given
        PersonalInventoryItem managed = PersonalInventoryItem.builder()
                .id(UUID.randomUUID())
                .ownerSub("preserved-sub")
                .name("Old")
                .note("Old note")
                .locationUexId(1)
                .locationType(PersonalInventoryLocationType.CITY)
                .locationNameSnapshot("OldName")
                .quantity(1)
                .build();
        managed.setVersion(5L);

        PersonalInventoryItemUpdateRequest req = new PersonalInventoryItemUpdateRequest(
                "New", "New note", 2, PersonalInventoryLocationType.SPACE_STATION, 9, 5L);

        // When
        mapper.updateEntity(managed, req);

        // Then
        assertEquals("New", managed.getName());
        assertEquals("New note", managed.getNote());
        assertEquals(2, managed.getLocationUexId());
        assertEquals(PersonalInventoryLocationType.SPACE_STATION, managed.getLocationType());
        assertEquals(9, managed.getQuantity());
        assertEquals("preserved-sub", managed.getOwnerSub(),
                "ownerSub must NEVER be overwritten by an update request");
        assertEquals(5L, managed.getVersion(),
                "version must be left to JPA; the mapper must not alter it");
        assertEquals("OldName", managed.getLocationNameSnapshot(),
                "the snapshot is owned by the service (set after UEX lookup), not by the mapper");
    }
}
