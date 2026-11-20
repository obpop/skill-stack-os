package com.skillstack.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevTokenServiceTest {
    private final DevTokenService tokens = new DevTokenService("test-secret-with-more-than-thirty-two-characters");

    @Test
    void signsAndVerifiesSubject() {
        assertThat(tokens.verify(tokens.issue("local:test-user"))).isEqualTo("local:test-user");
    }

    @Test
    void rejectsTamperedToken() {
        var token = tokens.issue("local:test-user");
        assertThatThrownBy(() -> tokens.verify(token + "tampered"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
