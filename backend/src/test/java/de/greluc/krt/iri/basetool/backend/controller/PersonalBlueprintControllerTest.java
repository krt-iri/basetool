package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintBatchCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintBatchResult;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintUpdateRequest;
import de.greluc.krt.iri.basetool.backend.service.PersonalBlueprintService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit tests for {@link PersonalBlueprintController}. */
@ExtendWith(MockitoExtension.class)
class PersonalBlueprintControllerTest {

  private static final String SUB = "user-sub-1";

  @Mock private PersonalBlueprintService service;
  @InjectMocks private PersonalBlueprintController controller;

  private JwtAuthenticationToken auth;

  @BeforeEach
  void setUp() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", SUB).build();
    auth = new JwtAuthenticationToken(jwt);
  }

  private static PersonalBlueprintResponse sample() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new PersonalBlueprintResponse(
        UUID.randomUUID(), "k", "Name", null, null, null, 0L, now, now);
  }

  @Test
  void list_derivesSubAndWrapsInPageResponse() {
    Page<PersonalBlueprintResponse> page =
        new PageImpl<>(List.of(sample()), PageRequest.of(0, 10), 1);
    when(service.listOwn(eq(SUB), any(), any())).thenReturn(page);

    PageResponse<PersonalBlueprintResponse> result = controller.list(0, 10, null, null, auth);

    assertEquals(1, result.totalElements());
    verify(service).listOwn(eq(SUB), any(), any());
  }

  @Test
  void add_relaysToServiceWithSub() {
    PersonalBlueprintCreateRequest req = new PersonalBlueprintCreateRequest("k", null, null);
    when(service.add(SUB, req)).thenReturn(sample());

    controller.add(req, auth);

    verify(service).add(SUB, req);
  }

  @Test
  void addBatch_relaysProductKeysWithSub() {
    PersonalBlueprintBatchCreateRequest req =
        new PersonalBlueprintBatchCreateRequest(List.of("a", "b"));
    when(service.addBatch(eq(SUB), eq(List.of("a", "b"))))
        .thenReturn(new PersonalBlueprintBatchResult(2, 0, 0));

    PersonalBlueprintBatchResult result = controller.addBatch(req, auth);

    assertEquals(2, result.added());
    verify(service).addBatch(SUB, List.of("a", "b"));
  }

  @Test
  void update_relaysToServiceWithSub() {
    UUID id = UUID.randomUUID();
    PersonalBlueprintUpdateRequest req = new PersonalBlueprintUpdateRequest(null, "n", 1L);
    when(service.update(SUB, id, req)).thenReturn(sample());

    controller.update(id, req, auth);

    verify(service).update(SUB, id, req);
  }

  @Test
  void delete_relaysToServiceWithSub() {
    UUID id = UUID.randomUUID();

    controller.delete(id, auth);

    verify(service).delete(SUB, id);
  }

  @Test
  void add_rejectsMissingJwtWithAccessDenied() {
    PersonalBlueprintCreateRequest req = new PersonalBlueprintCreateRequest("k", null, null);
    assertThrows(AccessDeniedException.class, () -> controller.add(req, null));
  }
}
