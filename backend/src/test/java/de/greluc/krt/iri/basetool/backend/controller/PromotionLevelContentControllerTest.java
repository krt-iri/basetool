package de.greluc.krt.iri.basetool.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentUpdateRequest;
import de.greluc.krt.iri.basetool.backend.service.PromotionLevelContentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Pure-Mockito unit tests for {@link PromotionLevelContentController}. Mirrors the topic / category
 * controller tests but additionally verifies the {@code PromotionLevel} enum flows through every
 * write endpoint — the enum lives on every Create/Update payload and on the Response, so a future
 * refactor that accidentally drops the level (e.g. mapping it to a String) would surface here.
 */
@ExtendWith(MockitoExtension.class)
class PromotionLevelContentControllerTest {

  @Mock private PromotionLevelContentService service;

  @InjectMocks private PromotionLevelContentController controller;

  private static PromotionLevelContentResponse content(UUID categoryId, PromotionLevel level) {
    Instant ts = Instant.parse("2026-05-15T12:00:00Z");
    return new PromotionLevelContentResponse(
        UUID.randomUUID(), 1L, categoryId, "cat", level, "description", ts, ts);
  }

  @Test
  void list_wrapsServicePageIntoPageResponse() {
    PromotionLevelContentResponse a = content(UUID.randomUUID(), PromotionLevel.LEVEL_A);
    Page<PromotionLevelContentResponse> page = new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1);
    when(service.list(any(Pageable.class))).thenReturn(page);

    PageResponse<PromotionLevelContentResponse> result = controller.list(0, 10, null);

    assertThat(result.content()).containsExactly(a);
    assertThat(result.totalElements()).isEqualTo(1L);
    verify(service).list(any(Pageable.class));
  }

  @Test
  void listByCategory_forwardsCategoryIdToService() {
    UUID categoryId = UUID.randomUUID();
    PromotionLevelContentResponse a = content(categoryId, PromotionLevel.LEVEL_A);
    PromotionLevelContentResponse b = content(categoryId, PromotionLevel.LEVEL_B);
    PromotionLevelContentResponse c = content(categoryId, PromotionLevel.LEVEL_C);
    when(service.listByCategory(categoryId)).thenReturn(List.of(a, b, c));

    List<PromotionLevelContentResponse> result = controller.listByCategory(categoryId);

    assertThat(result).containsExactly(a, b, c);
    verify(service).listByCategory(categoryId);
  }

  @Test
  void get_returnsServiceResponseVerbatim() {
    UUID id = UUID.randomUUID();
    PromotionLevelContentResponse response = content(UUID.randomUUID(), PromotionLevel.LEVEL_B);
    when(service.get(id)).thenReturn(response);

    PromotionLevelContentResponse result = controller.get(id);

    assertThat(result).isSameAs(response);
    verify(service).get(id);
  }

  @Test
  void create_preservesPromotionLevelOnWritePath() {
    UUID categoryId = UUID.randomUUID();
    PromotionLevelContentCreateRequest request =
        new PromotionLevelContentCreateRequest(categoryId, PromotionLevel.LEVEL_C, "description");
    PromotionLevelContentResponse created = content(categoryId, PromotionLevel.LEVEL_C);
    when(service.create(request)).thenReturn(created);

    PromotionLevelContentResponse result = controller.create(request);

    assertThat(result).isSameAs(created);
    assertThat(result.level()).isEqualTo(PromotionLevel.LEVEL_C);
    verify(service).create(request);
  }

  @Test
  void update_preservesPromotionLevelOnWritePath() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    PromotionLevelContentUpdateRequest request =
        new PromotionLevelContentUpdateRequest(2L, categoryId, PromotionLevel.LEVEL_A, "updated");
    PromotionLevelContentResponse updated = content(categoryId, PromotionLevel.LEVEL_A);
    when(service.update(id, request)).thenReturn(updated);

    PromotionLevelContentResponse result = controller.update(id, request);

    assertThat(result).isSameAs(updated);
    assertThat(result.level()).isEqualTo(PromotionLevel.LEVEL_A);
    verify(service).update(id, request);
  }

  @Test
  void delete_delegatesToService() {
    UUID id = UUID.randomUUID();

    controller.delete(id);

    verify(service).delete(id);
  }
}
