package de.greluc.krt.iri.basetool.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionCategoryCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionCategoryResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionCategoryUpdateRequest;
import de.greluc.krt.iri.basetool.backend.service.PromotionCategoryService;
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
import org.springframework.data.domain.Sort;

/**
 * Pure-Mockito unit tests for {@link PromotionCategoryController}. Symmetric to {@link
 * PromotionTopicControllerTest} — the controller is a thin pass-through, but it adds two
 * topic-scoped endpoints ({@code /by-topic/{topicId}} and {@code /by-topic/{topicId}/all}) whose
 * path-variable plumbing must reach the service untouched. The tests also cover the {@link
 * PageResponse} wrapping for the unscoped {@code list} endpoint so the frontend never sees a Spring
 * {@link Page} accidentally serialised in its raw form.
 */
@ExtendWith(MockitoExtension.class)
class PromotionCategoryControllerTest {

  @Mock private PromotionCategoryService service;

  @InjectMocks private PromotionCategoryController controller;

  private static PromotionCategoryResponse category(UUID topicId, String name) {
    Instant ts = Instant.parse("2026-05-15T12:00:00Z");
    return new PromotionCategoryResponse(
        UUID.randomUUID(), 1L, topicId, "topic", name, "desc", 1, ts, ts);
  }

  @Test
  void list_wrapsServicePageIntoPageResponse() {
    PromotionCategoryResponse cat = category(UUID.randomUUID(), "Marksmanship");
    Page<PromotionCategoryResponse> page =
        new PageImpl<>(
            List.of(cat), PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "sortOrder")), 1);
    when(service.list(any(Pageable.class))).thenReturn(page);

    PageResponse<PromotionCategoryResponse> result = controller.list(0, 20, "sortOrder,asc");

    assertThat(result.content()).containsExactly(cat);
    assertThat(result.totalElements()).isEqualTo(1L);
    assertThat(result.sort()).containsExactly("sortOrder,asc");
    verify(service).list(any(Pageable.class));
  }

  @Test
  void listByTopic_forwardsTopicIdAndPagination() {
    UUID topicId = UUID.randomUUID();
    PromotionCategoryResponse cat = category(topicId, "Marksmanship");
    Page<PromotionCategoryResponse> page = new PageImpl<>(List.of(cat), PageRequest.of(1, 5), 6);
    when(service.listByTopic(eq(topicId), any(Pageable.class))).thenReturn(page);

    PageResponse<PromotionCategoryResponse> result =
        controller.listByTopic(topicId, 1, 5, "sortOrder,asc");

    assertThat(result.content()).containsExactly(cat);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(5);
    assertThat(result.totalElements()).isEqualTo(6L);
    verify(service).listByTopic(eq(topicId), any(Pageable.class));
  }

  @Test
  void listAllByTopic_returnsServiceListVerbatim() {
    UUID topicId = UUID.randomUUID();
    PromotionCategoryResponse a = category(topicId, "Marksmanship");
    PromotionCategoryResponse b = category(topicId, "Driving");
    when(service.listAllByTopic(topicId)).thenReturn(List.of(a, b));

    List<PromotionCategoryResponse> result = controller.listAllByTopic(topicId);

    assertThat(result).containsExactly(a, b);
    verify(service).listAllByTopic(topicId);
  }

  @Test
  void get_returnsServiceResponseVerbatim() {
    UUID id = UUID.randomUUID();
    PromotionCategoryResponse response = category(UUID.randomUUID(), "Marksmanship");
    when(service.get(id)).thenReturn(response);

    PromotionCategoryResponse result = controller.get(id);

    assertThat(result).isSameAs(response);
    verify(service).get(id);
  }

  @Test
  void create_forwardsRequestToService() {
    UUID topicId = UUID.randomUUID();
    PromotionCategoryCreateRequest request =
        new PromotionCategoryCreateRequest(topicId, "Marksmanship", "desc", 1);
    PromotionCategoryResponse created = category(topicId, "Marksmanship");
    when(service.create(request)).thenReturn(created);

    PromotionCategoryResponse result = controller.create(request);

    assertThat(result).isSameAs(created);
    verify(service).create(request);
  }

  @Test
  void update_forwardsBothPathAndBodyToService() {
    UUID id = UUID.randomUUID();
    UUID topicId = UUID.randomUUID();
    PromotionCategoryUpdateRequest request =
        new PromotionCategoryUpdateRequest(7L, topicId, "Marksmanship Pro", "desc", 1);
    PromotionCategoryResponse updated = category(topicId, "Marksmanship Pro");
    when(service.update(id, request)).thenReturn(updated);

    PromotionCategoryResponse result = controller.update(id, request);

    assertThat(result).isSameAs(updated);
    verify(service).update(id, request);
  }

  @Test
  void delete_delegatesToService() {
    UUID id = UUID.randomUUID();

    controller.delete(id);

    verify(service).delete(id);
  }
}
