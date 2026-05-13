package de.greluc.krt.iri.basetool.frontend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TomcatRelaxedCharsTest {

  @LocalServerPort private int port;

  @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void testRawSocketRequestWithCurlyBraces() throws Exception {
    try (Socket socket = new Socket("localhost", port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

      // Sending a request with unencoded curly braces and asterisk
      out.println("GET /?id={{{11}}*{{11}}} HTTP/1.1");
      out.println("Host: localhost:" + port);
      out.println("Connection: close");
      out.println("");

      String firstLine = in.readLine();

      // If relaxed, Tomcat should parse it and return a standard HTTP response
      // (e.g., 200 OK, 302 Found/Redirect to login, or 404 Not Found)
      // but NOT 400 Bad Request which is Tomcat's default for invalid characters.
      assertThat(firstLine).isNotNull();
      assertThat(firstLine).doesNotContain("HTTP/1.1 400");

      // It should be a valid HTTP response
      assertThat(firstLine).startsWith("HTTP/1.1");
    }
  }
}
