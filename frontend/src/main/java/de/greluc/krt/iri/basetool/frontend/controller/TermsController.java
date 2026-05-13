package de.greluc.krt.iri.basetool.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Static Thymeleaf page controller for the terms of service ({@code /terms}). Renders a fixed
 * template without backend data.
 */
@Controller
public class TermsController {

  /**
   * @param model Thymeleaf model (unused; the template is static)
   * @return the {@code terms} view name
   */
  @GetMapping("/terms")
  public String showTerms(Model model) {
    return "terms";
  }
}
