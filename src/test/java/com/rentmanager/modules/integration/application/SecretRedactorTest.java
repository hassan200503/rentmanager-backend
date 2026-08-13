package com.rentmanager.modules.integration.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Field-level secret redaction: preview masking and audit-safe diffs. The
 * same redaction runs on every integration write path — no raw secret ever
 * reaches an API response or the audit log.
 */
class SecretRedactorTest {

    @Test
    void mask_nullOrBlank_returnsEmpty() {
        assertThat(SecretRedactor.mask(null)).isEmpty();
        assertThat(SecretRedactor.mask("  ")).isEmpty();
    }

    @Test
    void mask_shortValue_collapsesToFullMask() {
        assertThat(SecretRedactor.mask("a1b2")).isEqualTo("\u2022\u2022\u2022\u2022\u2022\u2022");
        assertThat(SecretRedactor.mask("123456")).isEqualTo("\u2022\u2022\u2022\u2022\u2022\u2022");
    }

    @Test
    void mask_longValue_keepsOnlyLastFourCharacters() {
        String masked = SecretRedactor.mask("sk_live_42a1");
        assertThat(masked).endsWith("42a1");
        assertThat(masked).doesNotContain("sk_live");
        assertThat(masked).startsWith("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
    }

    @Test
    void redactedDiff_secretChanged_marksChangedWithoutValue() {
        Map<String, Object> diff = SecretRedactor.redactedDiff(
                Map.of("api_key", "old"), Map.of("api_key", "new"),
                key -> key.equals("api_key"));

        assertThat(diff).containsEntry("api_key", "<redacted:changed>");
    }

    @Test
    void redactedDiff_secretUnchanged_marksUnchanged() {
        Map<String, Object> diff = SecretRedactor.redactedDiff(
                Map.of("api_key", "same"), Map.of("api_key", "same"),
                key -> key.equals("api_key"));

        assertThat(diff).containsEntry("api_key", "<redacted:unchanged>");
    }

    @Test
    void redactedDiff_nonSecretChanged_showsNewValue() {
        Map<String, Object> diff = SecretRedactor.redactedDiff(
                Map.of("base_url", "old"), Map.of("base_url", "new"),
                key -> false);

        assertThat(diff).containsEntry("base_url", "new");
    }

    @Test
    void redactedDiff_nonSecretUnchanged_isOmitted() {
        Map<String, Object> diff = SecretRedactor.redactedDiff(
                Map.of("base_url", "same"), Map.of("base_url", "same"),
                key -> false);

        assertThat(diff).doesNotContainKey("base_url");
    }

    @Test
    void redactedDiff_addedAndRemovedKeys_bothReported() {
        Map<String, Object> diff = SecretRedactor.redactedDiff(
                Map.of("removed", "x", "secret", "old"),
                Map.of("added", "y", "secret", "new"),
                key -> key.equals("secret"));

        assertThat(diff).containsKeys("removed", "added", "secret");
        assertThat(diff.get("removed")).isEqualTo("");
        assertThat(diff.get("added")).isEqualTo("y");
        assertThat(diff.get("secret")).isEqualTo("<redacted:changed>");
    }
}
