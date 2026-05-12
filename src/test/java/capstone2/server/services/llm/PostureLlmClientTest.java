package capstone2.server.services.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PostureLlmClientTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void enabledIsFalseForMissingOrBlankKey(String key) {
        var client = new PostureLlmClient(key, "gpt-4o-mini", 0.3, 30);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void enabledIsFalseForUnresolvedPlaceholder() {
        var client = new PostureLlmClient("${OPENAI_API_KEY:}", "gpt-4o-mini", 0.3, 30);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void enabledIsTrueForValidKey() {
        var client = new PostureLlmClient("sk-real-secret-1234567890", "gpt-4o-mini", 0.3, 30);
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void enabledIsTrueAfterTrimmingWhitespaceAroundValidKey() {
        var client = new PostureLlmClient("  sk-real-key-9876543210  ", "gpt-4o-mini", 0.3, 30);
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void maskKeyForNullReturnsNullLabel() {
        assertThat(PostureLlmClient.maskKey(null)).isEqualTo("<null>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void maskKeyForEmptyOrBlankReturnsEmptyLabel(String input) {
        assertThat(PostureLlmClient.maskKey(input)).isEqualTo("<empty>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"${OPENAI_API_KEY:}", "${OPENAI_API_KEY}"})
    void maskKeyForUnresolvedPlaceholderReturnsLabel(String input) {
        assertThat(PostureLlmClient.maskKey(input)).isEqualTo("<unresolved-placeholder>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12345678"})
    void maskKeyForShortKeyReturnsAsterisks(String input) {
        assertThat(PostureLlmClient.maskKey(input)).isEqualTo("***");
    }

    @ParameterizedTest
    @CsvSource({
            "sk-abcdefghij,sk-a...ghij",
            "sk-1234567890ABCD,sk-1...ABCD"
    })
    void maskKeyForLongKeyShowsFirstAndLastFour(String input, String expected) {
        assertThat(PostureLlmClient.maskKey(input)).isEqualTo(expected);
    }

    @Test
    void maskKeyNeverRevealsMiddleOfRealKey() {
        String secret = "sk-very-secret-do-not-show-MIDDLE-1234";
        String masked = PostureLlmClient.maskKey(secret);

        assertThat(masked).doesNotContain("very-secret");
        assertThat(masked).doesNotContain("MIDDLE");
        assertThat(masked).startsWith("sk-v");
        assertThat(masked).endsWith("1234");
    }
}
