package de.greluc.krt.iri.basetool.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {io.github.resilience4j.springboot3.verifier.autoconfigure.SpringBoot3VerifierAutoConfiguration.class})
public class FrontendApplication {
    static void main(String[] args) {
        SpringApplication.run(FrontendApplication.class, args);
    }
}
