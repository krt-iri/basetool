package de.greluc.krt.iri.basetool.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ImpressumController {

  @GetMapping("/impressum")
  public String showImpressum(Model model) {
    return "impressum";
  }
}
