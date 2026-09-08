package com.example.photoprocessor.imagem;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InputPixelLimitTest {
    @Test void acceptsBelowAndExactlyLimit() { var guard=new InputPixelGuard(25_000_000); guard.validate(100,100); guard.validate(5000,5000); }
    @Test void rejectsAboveAndOverflowSafely() {
        var guard=new InputPixelGuard(25_000_000);
        assertThatThrownBy(() -> guard.validate(5001,5000)).isInstanceOf(FunctionalProcessingException.class);
        assertThatThrownBy(() -> guard.validate(Long.MAX_VALUE,Long.MAX_VALUE)).isInstanceOf(FunctionalProcessingException.class);
    }
    @Test void rejectsInvalidConfiguration() { assertThatThrownBy(() -> new InputPixelGuard(0)).isInstanceOf(IllegalArgumentException.class); }
}
