package de.greluc.krt.iri.basetool.frontend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression coverage for the Redis-session {@link JsonMapper} configuration in {@link
 * RedisSessionConfig}. The original bug was a 500 surfacing on POST {@code /personal-inventory/add}
 * whenever the submitted form failed validation: the page controller pushed the {@link
 * BeanPropertyBindingResult} into a {@code RedirectAttributes} flash attribute, the redirect commit
 * serialised the FlashMap to Redis and Jackson exploded on the {@code BindingResult -> model ->
 * BindingResult -> ...} self-reference cycle with {@code Document nesting depth (501) exceeds the
 * maximum (500)}.
 *
 * <p>{@link RedisSessionConfig#buildSessionJsonMapper(ClassLoader)} now installs a Jackson mix-in
 * that hides {@code BindingResult.getModel()} from the serialiser, breaking the cycle without
 * dropping the field errors / target / object-name. These tests pin that behaviour so it cannot
 * silently regress when the configuration is touched in the future.
 */
class RedisSessionConfigTest {

  private final JsonMapper mapper =
      RedisSessionConfig.buildSessionJsonMapper(getClass().getClassLoader());

  @Test
  void bindingResultIsSerialisedWithoutTheSelfReferencingModelProperty() {
    SampleForm form = new SampleForm();
    form.setName("");
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(form, "sampleForm");
    bindingResult.rejectValue("name", "NotBlank", "must not be blank");

    // Mirror the exact shape FlashMap puts on the wire: an attribute under the
    // "<BindingResult-prefix><formName>" key plus the form attribute itself.
    Map<String, Object> flashAttributes = new HashMap<>();
    flashAttributes.put(BindingResult.MODEL_KEY_PREFIX + "sampleForm", bindingResult);
    flashAttributes.put("sampleForm", form);

    // Without the mix-in this would throw with
    //   `Document nesting depth (501) exceeds the maximum allowed (500, …)`.
    String json = mapper.writeValueAsString(flashAttributes);

    assertThat(json)
        .as("Mix-in must strip the synthesised `model` property that re-contains the BindingResult")
        .doesNotContain("\"model\"");
    assertThat(json)
        .as("Field errors must survive — th:errors needs them after the FlashMap round-trip")
        .contains("NotBlank")
        .contains("must not be blank");
    assertThat(json)
        .as("Object name must survive so Spring can re-attach the BindingResult to the right model")
        .contains("sampleForm");
  }

  /**
   * Simple form bean with a single property. We intentionally avoid one of the real frontend form
   * classes here so the test does not depend on their evolving validation annotations — the cycle
   * this test reproduces lives in Spring's {@code BeanPropertyBindingResult}, not in our forms.
   */
  @SuppressWarnings("unused")
  public static class SampleForm {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
