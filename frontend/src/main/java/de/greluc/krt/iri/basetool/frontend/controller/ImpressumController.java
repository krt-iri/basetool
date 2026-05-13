package de.greluc.krt.iri.basetool.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Static Thymeleaf page controller for the legal imprint ({@code /impressum}). Required by German
 * law (TMG §5) and renders a fixed template without backend data.
 */
@Controller
public class ImpressumController {

  /**
   * Returns the {@code impressum} view name.
   *
   * @param model Thymeleaf model (unused; the template is static)
   * @return the {@code impressum} view name
   */
  @GetMapping("/impressum")
  public String showImpressum(Model model) {
    return "impressum";
  }
}
