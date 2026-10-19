package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static RegisterRequest request(String firstName, String lastName) {
        return new RegisterRequest(firstName, lastName, "user@kickoffbet.test", "Parola123",
                LocalDate.now().minusYears(25));
    }

    private Set<String> invalidFields(RegisterRequest request) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ștefan", "Țurcanu", "Ăndrei", "Îonuț", "Mârza", "Jean-Luc", "O'Neil", "Ana Maria", "Zoë"})
    void acceptsRomanianAndAccentedNames(String name) {
        assertThat(invalidFields(request(name, name))).doesNotContain("firstName", "lastName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"John3", "<script>", "a_b", "Ion!", "Maria@"})
    void rejectsDigitsAndSymbols(String name) {
        assertThat(invalidFields(request(name, name))).contains("firstName", "lastName");
    }
}
