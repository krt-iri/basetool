package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserValidationTest {

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void testRankValidation_Valid() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("rankTesterValid");
        user.setRank(1); 
        userRepository.saveAndFlush(user);

        User user2 = new User();
        user2.setId(UUID.randomUUID());
        user2.setUsername("rankTesterValid2");
        user2.setRank(20);
        userRepository.saveAndFlush(user2);
    }

    @Test
    void testRankValidation_TooHigh() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("rankTesterHigh");
        user.setRank(21); 

        assertThrows(ConstraintViolationException.class, () -> userRepository.saveAndFlush(user));
    }

    @Test
    void testRankValidation_TooLow() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("rankTesterLow");
        user.setRank(0); 

        assertThrows(ConstraintViolationException.class, () -> userRepository.saveAndFlush(user));
    }
}
