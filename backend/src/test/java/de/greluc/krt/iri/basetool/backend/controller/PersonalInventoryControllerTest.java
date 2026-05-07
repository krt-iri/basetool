package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemUpdateRequest;
import de.greluc.krt.iri.basetool.backend.service.PersonalInventoryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalInventoryControllerTest {

    private static final String SUB = "user-sub-42";

    @Mock
    private PersonalInventoryItemService service;

    @InjectMocks
    private PersonalInventoryController controller;

    private JwtAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", SUB)
                .build();
        auth = new JwtAuthenticationToken(jwt);
    }

    @Test
    void listShouldDeriveOwnerSubFromJwtAndReturnPageResponse() {
        // Given
        Page<PersonalInventoryItemResponse> page = new PageImpl<>(
                List.of(sampleResponse()), PageRequest.of(0, 10), 1);
        when(service.listOwn(eq(SUB), any(), any())).thenReturn(page);

        // When
        PageResponse<PersonalInventoryItemResponse> result = controller.list(0, 10, null, null, auth);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
        verify(service).listOwn(eq(SUB), any(), any());
    }

    @Test
    void listShouldRejectMissingJwtWithAccessDenied() {
        assertThrows(AccessDeniedException.class,
                () -> controller.list(null, null, null, null, null));
    }

    @Test
    void createShouldDelegateToServiceWithJwtSub() {
        // Given
        PersonalInventoryItemCreateRequest req = new PersonalInventoryItemCreateRequest(
                "x", null, 1, PersonalInventoryLocationType.CITY, 1);
        PersonalInventoryItemResponse expected = sampleResponse();
        when(service.createOwn(SUB, req)).thenReturn(expected);

        // When
        PersonalInventoryItemResponse result = controller.create(req, auth);

        // Then
        assertSame(expected, result);
        ArgumentCaptor<String> subCaptor = ArgumentCaptor.forClass(String.class);
        verify(service).createOwn(subCaptor.capture(), eq(req));
        assertEquals(SUB, subCaptor.getValue(),
                "Owner identifier must come from JWT 'sub', never from the request body.");
    }

    @Test
    void updateShouldPropagatePathIdAndJwtSub() {
        UUID id = UUID.randomUUID();
        PersonalInventoryItemUpdateRequest req = new PersonalInventoryItemUpdateRequest(
                "y", null, 1, PersonalInventoryLocationType.CITY, 1, 0L);
        when(service.updateOwn(SUB, id, req)).thenReturn(sampleResponse());

        controller.update(id, req, auth);

        verify(service).updateOwn(SUB, id, req);
    }

    @Test
    void deleteShouldPropagatePathIdAndJwtSub() {
        UUID id = UUID.randomUUID();

        controller.delete(id, auth);

        verify(service).deleteOwn(SUB, id);
    }

    @Test
    void emptySubInJwtShouldBeRejected() {
        Jwt brokenJwt = Jwt.withTokenValue("t").header("alg", "none")
                .claims(c -> c.putAll(Map.of("sub", "")))
                .build();
        JwtAuthenticationToken broken = new JwtAuthenticationToken(brokenJwt);

        assertThrows(AccessDeniedException.class,
                () -> controller.list(null, null, null, null, broken));
    }

    private static PersonalInventoryItemResponse sampleResponse() {
        return new PersonalInventoryItemResponse(
                UUID.randomUUID(), "x", null, 1, PersonalInventoryLocationType.CITY,
                "Lorville", 1, 0L, Instant.now(), Instant.now());
    }
}
