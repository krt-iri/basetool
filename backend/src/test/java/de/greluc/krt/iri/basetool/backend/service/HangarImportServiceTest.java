package de.greluc.krt.iri.basetool.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewImportResponseDto;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@ExtendWith(MockitoExtension.class)
class HangarImportServiceTest {

    @Mock
    private ShipRepository shipRepository;
    @Mock
    private ShipTypeRepository shipTypeRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HangarImportService hangarImportService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void injectObjectMapper() throws Exception {
        // Inject the real ObjectMapper into the service via reflection
        var field = HangarImportService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(hangarImportService, objectMapper);
    }

    // -------------------------------------------------------------------------
    // Happy path: all ships matched, none in hangar yet → all imported
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_allMatched_importsAllShips() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type135c = shipTypeWithName("135c");
        ShipType typeZeus = shipTypeWithName("zeus mk ii mr");

        String json = """
                [
                  {"name":"135c","shipname":"","type":"ship"},
                  {"name":"zeus mk ii mr","shipname":"My Zeus","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("135c")).thenReturn(Optional.of(type135c));
        when(shipTypeRepository.findByNameIgnoreCase("zeus mk ii mr")).thenReturn(Optional.of(typeZeus));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type135c.getId())).thenReturn(0L);
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, typeZeus.getId())).thenReturn(0L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.duplicateCount()).isEqualTo(0);
        assertThat(result.skippedShips()).isEmpty();
        assertThat(result.duplicateShips()).isEmpty();
        verify(shipRepository, times(2)).save(any(Ship.class));
    }

    // -------------------------------------------------------------------------
    // Partial match: one matched, one not found in DB
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_partialMatch_skipsUnknownShips() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type135c = shipTypeWithName("135c");

        String json = """
                [
                  {"name":"135c","shipname":"","type":"ship"},
                  {"name":"unknown alien ship","shipname":"","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("135c")).thenReturn(Optional.of(type135c));
        when(shipTypeRepository.findByNameIgnoreCase("unknown alien ship")).thenReturn(Optional.empty());
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type135c.getId())).thenReturn(0L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedShips()).containsExactly("unknown alien ship");
        verify(shipRepository, times(1)).save(any(Ship.class));
    }

    // -------------------------------------------------------------------------
    // Duplicate handling: JSON has 3×, hangar has 0 → 3 created
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_triplicateInJson_hangarEmpty_createsAllThree() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type = shipTypeWithName("aurora mr");

        String json = """
                [
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("aurora mr")).thenReturn(Optional.of(type));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type.getId())).thenReturn(0L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then
        assertThat(result.importedCount()).isEqualTo(3);
        assertThat(result.duplicateCount()).isEqualTo(0);
        verify(shipRepository, times(3)).save(any(Ship.class));
    }

    // -------------------------------------------------------------------------
    // Duplicate handling: JSON has 3×, hangar has 1 → 2 created
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_triplicateInJson_hangarHasOne_createsTwoMore() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type = shipTypeWithName("aurora mr");

        String json = """
                [
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("aurora mr")).thenReturn(Optional.of(type));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type.getId())).thenReturn(1L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.duplicateCount()).isEqualTo(0);
        verify(shipRepository, times(2)).save(any(Ship.class));
    }

    // -------------------------------------------------------------------------
    // Duplicate handling: JSON has 3×, hangar has 3 → none created (skipped)
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_triplicateInJson_hangarAlreadyHasThree_createsNone() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type = shipTypeWithName("aurora mr");

        String json = """
                [
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("aurora mr")).thenReturn(Optional.of(type));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type.getId())).thenReturn(3L);

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then
        assertThat(result.importedCount()).isEqualTo(0);
        assertThat(result.duplicateCount()).isEqualTo(3);
        verify(shipRepository, never()).save(any(Ship.class));
    }

    // -------------------------------------------------------------------------
    // Duplicate handling: JSON has 3×, hangar has 5 → none created, no deletion
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_triplicateInJson_hangarHasFive_createsNoneAndDoesNotDelete() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type = shipTypeWithName("aurora mr");

        String json = """
                [
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("aurora mr")).thenReturn(Optional.of(type));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type.getId())).thenReturn(5L);

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then
        assertThat(result.importedCount()).isEqualTo(0);
        assertThat(result.duplicateCount()).isEqualTo(3);
        verify(shipRepository, never()).save(any(Ship.class));
        verify(shipRepository, never()).delete(any());
        verify(shipRepository, never()).deleteAll(any());
    }

    // -------------------------------------------------------------------------
    // Mixed: JSON has 1× ship A and 3× ship B; hangar has 2× A, 1× B
    // → 0 more A (don't delete surplus), 2 more B
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_mixed_partialCreation() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType typeA = shipTypeWithName("vulture");
        ShipType typeB = shipTypeWithName("aurora mr");

        String json = """
                [
                  {"name":"vulture","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"},
                  {"name":"aurora mr","shipname":"","type":"ship"}
                ]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("vulture")).thenReturn(Optional.of(typeA));
        when(shipTypeRepository.findByNameIgnoreCase("aurora mr")).thenReturn(Optional.of(typeB));
        // Hangar: 2× vulture (JSON only has 1 → surplus, no creation), 1× aurora mr (JSON has 3 → 2 more)
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, typeA.getId())).thenReturn(2L);
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, typeB.getId())).thenReturn(1L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FleetviewImportResponseDto result = hangarImportService.importFleetview(userId, file);

        // Then: 0 vulture created (surplus), 2 aurora mr created
        assertThat(result.importedCount()).isEqualTo(2);
        // vulture (1 in JSON) is "already sufficient" → counted in duplicateCount
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(shipRepository, times(2)).save(any(Ship.class));
    }

    // -------------------------------------------------------------------------
    // Default insurance value
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_setsDefaultInsurance() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type = shipTypeWithName("vulture");

        String json = """
                [{"name":"vulture","shipname":"","type":"ship"}]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("vulture")).thenReturn(Optional.of(type));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type.getId())).thenReturn(0L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        hangarImportService.importFleetview(userId, file);

        // Then
        ArgumentCaptor<Ship> captor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(captor.capture());
        assertThat(captor.getValue().getInsurance()).isEqualTo(HangarImportService.DEFAULT_INSURANCE);
    }

    // -------------------------------------------------------------------------
    // Individual ship name is transferred if present
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_setsIndividualShipName_whenPresent() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        ShipType type = shipTypeWithName("890 jump");

        String json = """
                [{"name":"890 jump","shipname":"Stella Aeterna","type":"ship"}]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipTypeRepository.findByNameIgnoreCase("890 jump")).thenReturn(Optional.of(type));
        when(shipRepository.countByOwnerIdAndShipTypeId(userId, type.getId())).thenReturn(0L);
        when(shipRepository.save(any(Ship.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        hangarImportService.importFleetview(userId, file);

        // Then
        ArgumentCaptor<Ship> captor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Stella Aeterna");
    }

    // -------------------------------------------------------------------------
    // Empty file → 400
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_emptyFile_throws400() {
        // Given
        UUID userId = UUID.randomUUID();
        MockMultipartFile emptyFile = new MockMultipartFile("file", "fleetview.json",
                "application/json", new byte[0]);

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> hangarImportService.importFleetview(userId, emptyFile));
    }

    // -------------------------------------------------------------------------
    // Invalid JSON → 400
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_invalidJson_throws400() {
        // Given
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = multipartFile("THIS IS NOT JSON");

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> hangarImportService.importFleetview(userId, file));
    }

    // -------------------------------------------------------------------------
    // Unknown user → 404
    // -------------------------------------------------------------------------

    @Test
    void importFleetview_unknownUser_throws404() {
        // Given
        UUID userId = UUID.randomUUID();
        String json = """
                [{"name":"135c","shipname":"","type":"ship"}]
                """;
        MockMultipartFile file = multipartFile(json);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When / Then
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> hangarImportService.importFleetview(userId, file));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ShipType shipTypeWithName(String name) {
        ShipType type = new ShipType();
        type.setId(UUID.randomUUID());
        type.setName(name);
        return type;
    }

    private static MockMultipartFile multipartFile(String json) {
        return new MockMultipartFile("file", "fleetview.json",
                "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
