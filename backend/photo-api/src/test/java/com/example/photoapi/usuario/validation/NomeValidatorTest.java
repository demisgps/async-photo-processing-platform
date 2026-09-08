package com.example.photoapi.usuario.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.photoapi.exception.ApiException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NomeValidatorTest {
    private final NomeValidator validator = new NomeValidator();

    @Test void trimsAndAcceptsUnicodeCodePoints() {
        assertThat(validator.validate("  João 😀  ")).isEqualTo("João 😀");
    }

    @Test void acceptsExactlyTwoAndOneHundredFiftyCodePoints() {
        assertThat(validator.validate("😀a").codePoints()).hasSize(2);
        String max = IntStream.range(0, 150).mapToObj(i -> "á").reduce("", String::concat);
        assertThat(validator.validate(max).codePoints()).hasSize(150);
    }

    @Test void rejectsNullBlankShortAndOverLimit() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("   ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(" a ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("a".repeat(151))).isInstanceOf(ApiException.class);
    }
}
