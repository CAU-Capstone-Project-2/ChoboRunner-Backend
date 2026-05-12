package capstone2.server.services.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiAnalysisClientTest {

    @Test
    void truncateReturnsNullForNull() {
        assertThat(AiAnalysisClient.truncate(null, 10)).isNull();
    }

    @Test
    void truncateReturnsOriginalWhenShorterThanLimit() {
        assertThat(AiAnalysisClient.truncate("hello", 10)).isEqualTo("hello");
    }

    @Test
    void truncateReturnsOriginalWhenExactlyLimit() {
        assertThat(AiAnalysisClient.truncate("0123456789", 10)).isEqualTo("0123456789");
    }

    @Test
    void truncateAppendsSuffixWhenLonger() {
        String input = "0123456789ABCDE";
        String out = AiAnalysisClient.truncate(input, 10);
        assertThat(out).startsWith("0123456789");
        assertThat(out).contains("(truncated 5 chars)");
    }
}
