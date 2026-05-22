package de.greluc.krt.iri.basetool.frontend.model.form;

/**
 * Form-binding object for the per-membership Logistician / Mission Manager flag flip on the admin
 * Spezialkommando detail page.
 *
 * <p>Modeled as a {@code record} on purpose. The HTML form carries field names {@code
 * isLogistician} and {@code isMissionManager} verbatim, but a Lombok {@code @Data} POJO with a
 * {@code boolean isLogistician} field would expose the JavaBean property as {@code logistician}
 * (Lombok strips the {@code is} prefix in the setter, and Spring derives the property name from the
 * setter). That mismatch made the obvious form name unbindable. A record uses each component name
 * as the property name directly — {@code isLogistician} stays {@code isLogistician} — and Spring's
 * {@link org.springframework.web.bind.ServletRequestDataBinder#checkFieldDefaults} step still picks
 * up the {@code _<field>} hidden marker the form template emits before each checkbox, so an
 * unchecked box surfaces as {@code false} instead of being missing from the payload.
 *
 * <p>Primitive {@code boolean} (not boxed {@code Boolean}): the hidden {@code _field} marker
 * guarantees the field is always present, so the bind cannot leave the component as {@code null} —
 * and a primitive removes the ambiguous "null = no change" semantic that the backend patch DTO
 * needs for direct API callers, but the admin UI does not.
 *
 * @param isLogistician new value of the Logistician flag.
 * @param isMissionManager new value of the Mission Manager flag.
 * @param version optimistic-lock counter held by the form; required for the backend's version
 *     check.
 */
public record MembershipFlagsForm(boolean isLogistician, boolean isMissionManager, Long version) {}
