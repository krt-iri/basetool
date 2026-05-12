package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserAttributesUpdateDto;
import de.greluc.krt.iri.basetool.frontend.model.form.MemberEditForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.Model;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberManagementControllerTest {

    private BackendApiClient backendApiClient;
    private MemberManagementController controller;
    private RedirectAttributes redirectAttributes;

    @BeforeEach
    void setUp() {
        backendApiClient = mock(BackendApiClient.class);
        controller = new MemberManagementController(backendApiClient);
        redirectAttributes = new RedirectAttributesModelMap();
    }

    // ---------------------------------------------------------------
    // deleteMember (existing tests preserved verbatim)
    // ---------------------------------------------------------------

    @Test
    void deleteMember_ShouldRedirectAndAddSuccessToast() {
        UUID userId = UUID.randomUUID();

        String view = controller.deleteMember(userId, redirectAttributes);

        verify(backendApiClient).delete("/api/v1/users/" + userId, Void.class);
        assertEquals("redirect:/members", view);
        assertEquals("success.user.delete", redirectAttributes.getFlashAttributes().get("successToast"));
    }

    @Test
    void deleteMember_OnFailure_ShouldRedirectAndAddErrorToast() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("API Error")).when(backendApiClient).delete(anyString(), any());

        String view = controller.deleteMember(userId, redirectAttributes);

        verify(backendApiClient).delete("/api/v1/users/" + userId, Void.class);
        assertEquals("redirect:/members", view);
        assertEquals("error.user.delete", redirectAttributes.getFlashAttributes().get("errorToast"));
    }

    // ---------------------------------------------------------------
    // listMembers — search query assembly + page injection + error path
    // ---------------------------------------------------------------

    @Nested
    class ListMembersTests {

        @Test
        void noSearch_appendsSortOnlyToBaseUri() {
            Model model = new ConcurrentModel();
            PageResponse<UserDto> page = newPage(List.of(newUser("alice")));
            when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                    .thenReturn(page);

            String view = controller.listMembers(null, null, null, model);

            assertEquals("members", view);
            ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
            verify(backendApiClient).get(uriCaptor.capture(), any(ParameterizedTypeReference.class));
            assertEquals("/api/v1/users?sort=username,asc", uriCaptor.getValue());
            assertEquals(page.content(), model.getAttribute("users"));
            assertSame(page, model.getAttribute("usersPage"));
            assertNull(model.getAttribute("search"));
        }

        @Test
        void withSearch_routesToSearchEndpointWithQueryParam() {
            Model model = new ConcurrentModel();
            when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                    .thenReturn(newPage(List.of()));

            controller.listMembers("alice", null, null, model);

            ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
            verify(backendApiClient).get(uriCaptor.capture(), any(ParameterizedTypeReference.class));
            String uri = uriCaptor.getValue();
            assertTrue(uri.startsWith("/api/v1/users/search?query=alice"),
                    "uri must start with /api/v1/users/search?query=alice, got: " + uri);
            assertTrue(uri.endsWith("sort=username,asc"));
            assertEquals("alice", model.getAttribute("search"));
        }

        @Test
        void blankSearch_routesToListEndpoint() {
            // Treat blank search as "no search" — uses the listing endpoint, not search.
            Model model = new ConcurrentModel();
            when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                    .thenReturn(newPage(List.of()));

            controller.listMembers("   ", null, null, model);

            ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
            verify(backendApiClient).get(uriCaptor.capture(), any(ParameterizedTypeReference.class));
            assertTrue(uriCaptor.getValue().startsWith("/api/v1/users?"));
        }

        @Test
        void pageAndSizeParams_appendedToUri() {
            Model model = new ConcurrentModel();
            when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                    .thenReturn(newPage(List.of()));

            controller.listMembers(null, 2, 25, model);

            ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
            verify(backendApiClient).get(uriCaptor.capture(), any(ParameterizedTypeReference.class));
            String uri = uriCaptor.getValue();
            assertTrue(uri.contains("page=2"), "page param missing in: " + uri);
            assertTrue(uri.contains("size=25"));
        }

        @Test
        void nullPageResponse_setsNullUsersAndNullPage() {
            // Defensive: if the backend returns null (e.g. mid-degradation), the model
            // must NOT NPE — users stays null and the view still renders.
            Model model = new ConcurrentModel();
            when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                    .thenReturn(null);

            String view = controller.listMembers(null, null, null, model);

            assertEquals("members", view);
            assertNull(model.getAttribute("users"));
            assertNull(model.getAttribute("usersPage"));
        }

        @Test
        void backendError_setsErrorAttribute_andStillReturnsView() {
            Model model = new ConcurrentModel();
            doThrow(new RuntimeException("backend down")).when(backendApiClient)
                    .get(anyString(), any(ParameterizedTypeReference.class));

            String view = controller.listMembers(null, null, null, model);

            assertEquals("members", view, "error path must NOT redirect — direct view render");
            assertEquals("error.members.load", model.getAttribute("error"));
        }
    }

    // ---------------------------------------------------------------
    // searchMembers — JSON API endpoint
    // ---------------------------------------------------------------

    @Test
    void searchMembers_returnsContentList() {
        PageResponse<UserDto> page = newPage(List.of(newUser("alice"), newUser("bob")));
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                .thenReturn(page);

        List<UserDto> result = controller.searchMembers("ali");

        assertEquals(2, result.size());
    }

    @Test
    void searchMembers_nullResponse_returnsNull() {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                .thenReturn(null);

        assertNull(controller.searchMembers("ali"));
    }

    // ---------------------------------------------------------------
    // editMember
    // ---------------------------------------------------------------

    @Nested
    class EditMemberTests {

        @Test
        void happyPath_setsUserAndPrefilledForm() {
            UUID id = UUID.randomUUID();
            UserDto user = newUser("alice");
            Model model = new ConcurrentModel();
            when(backendApiClient.get(eq("/api/v1/users/" + id), eq(UserDto.class))).thenReturn(user);

            String view = controller.editMember(id, null, model, redirectAttributes);

            assertEquals("member-edit", view);
            assertSame(user, model.getAttribute("user"));
            MemberEditForm form = (MemberEditForm) model.getAttribute("memberEditForm");
            assertNotNull(form);
            assertEquals(user.rank(), form.rank());
            assertEquals(user.description(), form.description());
            assertEquals(user.version(), form.version());
        }

        @Test
        void prefilledFormWithNullSource_isReplacedWithSourceParam() {
            // If a form is already in the model (e.g. after a redirect with flash) but
            // its source is null, the controller substitutes the request's source param.
            UUID id = UUID.randomUUID();
            UserDto user = newUser("alice");
            Model model = new ConcurrentModel();

            MemberEditForm existingForm = new MemberEditForm(5, "old desc", "alice", 1L, null, null);
            model.addAttribute("memberEditForm", existingForm);

            when(backendApiClient.get(eq("/api/v1/users/" + id), eq(UserDto.class))).thenReturn(user);

            controller.editMember(id, "profile", model, redirectAttributes);

            MemberEditForm form = (MemberEditForm) model.getAttribute("memberEditForm");
            assertEquals("profile", form.source(),
                    "null source on the existing form must be replaced by the request param");
            assertEquals(existingForm.rank(), form.rank(),
                    "other fields must be preserved verbatim");
        }

        @Test
        void prefilledFormWithExistingSource_isNotTouched() {
            UUID id = UUID.randomUUID();
            UserDto user = newUser("alice");
            Model model = new ConcurrentModel();

            MemberEditForm existingForm = new MemberEditForm(5, "old desc", "alice", 1L,
                    "existing-source", null);
            model.addAttribute("memberEditForm", existingForm);

            when(backendApiClient.get(eq("/api/v1/users/" + id), eq(UserDto.class))).thenReturn(user);

            controller.editMember(id, "different-source", model, redirectAttributes);

            MemberEditForm form = (MemberEditForm) model.getAttribute("memberEditForm");
            assertSame(existingForm, form,
                    "existing form with non-null source must NOT be replaced");
        }

        @Test
        void backendError_redirectsToListWithErrorToast() {
            UUID id = UUID.randomUUID();
            Model model = new ConcurrentModel();
            doThrow(new RuntimeException("not found")).when(backendApiClient)
                    .get(anyString(), any(Class.class));

            String view = controller.editMember(id, null, model, redirectAttributes);

            assertEquals("redirect:/members", view);
            assertEquals("error.member.details.load",
                    redirectAttributes.getFlashAttributes().get("errorToast"));
        }
    }

    // ---------------------------------------------------------------
    // updateMember — validation errors + happy path + backend failure + source routing
    // ---------------------------------------------------------------

    @Nested
    class UpdateMemberTests {

        @Test
        void validationErrors_reRenderForm_doNotRedirect() {
            // bindingResult.hasErrors() -> direct render via editMember(...) without
            // flash. The BindingResult stays request-scoped (the fix for the
            // RedisSessionConfig flash-cycle bug).
            UUID id = UUID.randomUUID();
            UserDto user = newUser("alice");
            Model model = new ConcurrentModel();
            MemberEditForm form = new MemberEditForm(5, "x", "alice", 1L, null, null);

            when(backendApiClient.get(eq("/api/v1/users/" + id), eq(UserDto.class))).thenReturn(user);
            BindingResult br = mock(BindingResult.class);
            when(br.hasErrors()).thenReturn(true);

            String view = controller.updateMember(id, form, br, model, redirectAttributes);

            assertEquals("member-edit", view,
                    "validation errors must re-render directly, NOT redirect");
            verify(backendApiClient, never()).put(anyString(), any(), any());
            assertTrue(redirectAttributes.getFlashAttributes().isEmpty(),
                    "must NOT add any flash attribute (the BindingResult lives request-scoped)");
        }

        @Test
        void happyPath_putsAttributesAndRedirectsToList() {
            UUID id = UUID.randomUUID();
            Model model = new ConcurrentModel();
            MemberEditForm form = new MemberEditForm(5, "desc", "Alice", 1L, null, null);
            BindingResult br = mock(BindingResult.class);
            when(br.hasErrors()).thenReturn(false);

            String view = controller.updateMember(id, form, br, model, redirectAttributes);

            assertEquals("redirect:/members", view);
            // Verify the PUT call's body shape.
            ArgumentCaptor<UserAttributesUpdateDto> body =
                    ArgumentCaptor.forClass(UserAttributesUpdateDto.class);
            verify(backendApiClient).put(eq("/api/v1/users/" + id + "/attributes"),
                    body.capture(), eq(Void.class));
            assertEquals(5, body.getValue().rank());
            assertEquals("desc", body.getValue().description());
            assertEquals("Alice", body.getValue().displayName());
            assertEquals(1L, body.getValue().version());
            assertEquals("notification.success.save",
                    redirectAttributes.getFlashAttributes().get("successToast"));
        }

        @Test
        void happyPath_withProfileSource_redirectsToProfile() {
            UUID id = UUID.randomUUID();
            Model model = new ConcurrentModel();
            MemberEditForm form = new MemberEditForm(5, "desc", "Alice", 1L, "profile", null);
            BindingResult br = mock(BindingResult.class);
            when(br.hasErrors()).thenReturn(false);

            String view = controller.updateMember(id, form, br, model, redirectAttributes);

            assertEquals("redirect:/profile", view,
                    "source=profile must route the post-save redirect to /profile");
        }

        @Test
        void backendError_redirectsToEditWithErrorToast() {
            UUID id = UUID.randomUUID();
            Model model = new ConcurrentModel();
            MemberEditForm form = new MemberEditForm(5, "desc", "Alice", 1L, null, null);
            BindingResult br = mock(BindingResult.class);
            when(br.hasErrors()).thenReturn(false);
            doThrow(new RuntimeException("backend down")).when(backendApiClient)
                    .put(anyString(), any(), any());

            String view = controller.updateMember(id, form, br, model, redirectAttributes);

            assertEquals("redirect:/members/" + id + "/edit", view);
            assertEquals("error.member.update.failed",
                    redirectAttributes.getFlashAttributes().get("errorToast"));
        }

        @Test
        void backendError_withSource_redirectsToEditPreservingSource() {
            UUID id = UUID.randomUUID();
            Model model = new ConcurrentModel();
            MemberEditForm form = new MemberEditForm(5, "desc", "Alice", 1L, "profile", null);
            BindingResult br = mock(BindingResult.class);
            when(br.hasErrors()).thenReturn(false);
            doThrow(new RuntimeException("nope")).when(backendApiClient)
                    .put(anyString(), any(), any());

            String view = controller.updateMember(id, form, br, model, redirectAttributes);

            assertEquals("redirect:/members/" + id + "/edit?source=profile", view,
                    "the source param must be preserved on the failure redirect so the user "
                            + "lands back on the same view");
        }
    }

    // ---------------------------------------------------------------
    // toggleLogistician / toggleMissionManager
    // ---------------------------------------------------------------

    @Nested
    class ToggleTests {

        @Test
        void toggleLogistician_true_callsBackend() {
            UUID id = UUID.randomUUID();
            UserDto expected = newUser("alice");
            when(backendApiClient.patch(anyString(), eq(null), eq(UserDto.class))).thenReturn(expected);

            UserDto result = controller.toggleLogistician(id, true);

            assertSame(expected, result);
            verify(backendApiClient).patch(
                    "/api/v1/users/" + id + "/logistician?isLogistician=true",
                    null, UserDto.class);
        }

        @Test
        void toggleLogistician_false_callsBackend() {
            UUID id = UUID.randomUUID();
            UserDto expected = newUser("alice");
            when(backendApiClient.patch(anyString(), eq(null), eq(UserDto.class))).thenReturn(expected);

            controller.toggleLogistician(id, false);

            verify(backendApiClient).patch(
                    "/api/v1/users/" + id + "/logistician?isLogistician=false",
                    null, UserDto.class);
        }

        @Test
        void toggleMissionManager_callsBackend() {
            UUID id = UUID.randomUUID();
            UserDto expected = newUser("alice");
            when(backendApiClient.patch(anyString(), eq(null), eq(UserDto.class))).thenReturn(expected);

            controller.toggleMissionManager(id, true);

            verify(backendApiClient).patch(
                    "/api/v1/users/" + id + "/mission-manager?isMissionManager=true",
                    null, UserDto.class);
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static UserDto newUser(String name) {
        return new UserDto(
                UUID.randomUUID(), name, name + " display", name + " display",
                "First", "Last", name + "@example.com",
                5, "desc", Set.of("ROLE_SQUADRON_MEMBER"), Set.of(),
                null, false, false, true, 1L, null);
    }

    private static PageResponse<UserDto> newPage(List<UserDto> content) {
        return new PageResponse<>(content, 0, 20, content.size(), 1, List.of("username,asc"));
    }
}
