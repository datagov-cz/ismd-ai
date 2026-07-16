package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.config.TokenUsageProperties;
import cz.dia.ismd.assistant.exception.TokenLimitReachedException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenUsageServiceTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TokenUsageService service = new TokenUsageService(
            jdbcTemplate,
            new TokenUsageProperties(100)
    );

    @Test
    void rejectsRequestWhenStoredUsageHasReachedLimit() {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("test-user"))).thenReturn(100);

        assertThatThrownBy(() -> service.ensureRequestAllowed("test-user"))
                .isInstanceOf(TokenLimitReachedException.class)
                .hasMessageContaining("test-user")
                .hasMessageContaining("100");
    }

    @Test
    void allowsRequestWhenUserHasNoStoredUsage() {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("new-user"))).thenReturn(0);

        service.ensureRequestAllowed("new-user");
    }

    @Test
    void atomicallyAddsOutputTokens() {
        service.addOutputTokens("test-user", 23);

        verify(jdbcTemplate).update(anyString(), eq("test-user"), eq(23));
    }

    @Test
    void doesNotWriteZeroOutputTokens() {
        service.addOutputTokens("test-user", 0);

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
