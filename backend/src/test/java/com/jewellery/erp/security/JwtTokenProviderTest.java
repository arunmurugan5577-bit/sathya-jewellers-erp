package com.jewellery.erp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("a-test-signing-key-that-is-long-enough-256".getBytes());

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties(SECRET, "jewellery-erp-test", Duration.ofMinutes(15), Duration.ofDays(7)));

    private static UserPrincipal principal() {
        return UserPrincipal.fromClaims(
                42L, "staff1", "Staff One", Set.of("ROLE_USER"), Set.of("CATEGORY_VIEW", "INVENTORY_VIEW"));
    }

    @Test
    @DisplayName("a token round-trips the identity and the permission codes")
    void roundTripsClaims() {
        String token = provider.createAccessToken(principal()).token();

        Optional<UserPrincipal> parsed = provider.parse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().id()).isEqualTo(42L);
        assertThat(parsed.get().username()).isEqualTo("staff1");
        assertThat(parsed.get().permissions()).containsExactlyInAnyOrder("CATEGORY_VIEW", "INVENTORY_VIEW");
        assertThat(parsed.get().roles()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("a tampered payload is rejected rather than trusted")
    void rejectsTamperedToken() {
        String token = provider.createAccessToken(principal()).token();
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin\",\"perms\":[\"USER_DELETE\"]}".getBytes());

        assertThat(provider.parse(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void rejectsForeignSignature() {
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(
                Base64.getEncoder().encodeToString("a-completely-different-key-256-bits-long".getBytes()),
                "jewellery-erp-test",
                Duration.ofMinutes(15),
                Duration.ofDays(7)));

        String foreignToken = other.createAccessToken(principal()).token();

        assertThat(provider.parse(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("an already expired token is rejected")
    void rejectsExpiredToken() {
        JwtTokenProvider expiring = new JwtTokenProvider(
                new JwtProperties(SECRET, "jewellery-erp-test", Duration.ofSeconds(-1), Duration.ofDays(7)));

        String token = expiring.createAccessToken(principal()).token();

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("a token from a different issuer is rejected")
    void rejectsForeignIssuer() {
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtProperties(SECRET, "some-other-system", Duration.ofMinutes(15), Duration.ofDays(7)));

        assertThat(provider.parse(other.createAccessToken(principal()).token())).isEmpty();
    }

    @Test
    @DisplayName("a secret shorter than 256 bits fails fast at start-up")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                        new JwtProperties("short", "jewellery-erp-test", Duration.ofMinutes(15), Duration.ofDays(7))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    @DisplayName("garbage in the Authorization header does not throw")
    void rejectsGarbage() {
        assertThat(provider.parse("not-a-token")).isEmpty();
        assertThat(provider.parse("")).isEmpty();
    }
}
