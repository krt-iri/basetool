package de.greluc.krt.iri.basetool.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Static Thymeleaf page controller for the privacy policy ({@code /privacy}). Required by GDPR
 * (DSGVO Art. 13/14) and renders a fixed template without backend data.
 */
@Controller
public class PrivacyController {

  /**
   * @param model Thymeleaf model (unused; the template is static)
   * @return the {@code privacy} view name
   */
  @GetMapping("/privacy")
  public String showPrivacy(Model model) {
    return "privacy";
  }
}
