package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.config.TokenUsageProperties;
import cz.dia.ismd.assistant.exception.TokenLimitReachedException;
import cz.dia.ismd.assistant.model.job.TokenUsage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class TokenUsageService {

    private final JdbcTemplate jdbcTemplate;
    private final TokenUsageProperties properties;

    public TokenUsageService(JdbcTemplate jdbcTemplate, TokenUsageProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public void ensureRequestAllowed(String userId) {
        String validatedUserId = validateUserId(userId);
        Integer tokensSpent = jdbcTemplate.queryForObject("""
                SELECT COALESCE(
                    (SELECT tokens_spent
                     FROM token_usage
                     WHERE user_id = ? AND usage_date = CURRENT_DATE),
                    0
                )
                """, Integer.class, validatedUserId);

        if (tokensSpent != null && tokensSpent >= properties.maxAllowedPerDay()) {
            throw new TokenLimitReachedException(validatedUserId, properties.maxAllowedPerDay());
        }
    }

    @Transactional
    public void addOutputTokens(String userId, int outputTokens) {
        String validatedUserId = validateUserId(userId);
        if (outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens must not be negative");
        }
        if (outputTokens == 0) {
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO token_usage(user_id, usage_date, tokens_spent)
                VALUES (?, CURRENT_DATE, ?)
                ON CONFLICT (user_id, usage_date) DO UPDATE
                SET tokens_spent = token_usage.tokens_spent + EXCLUDED.tokens_spent
                """, validatedUserId, outputTokens);
    }

    @Transactional(readOnly = true)
    public TokenUsage getUsage(String userId) {
        String validatedUserId = validateUserId(userId);
        return jdbcTemplate.query("""
                        SELECT user_id, tokens_spent
                        FROM token_usage
                        WHERE user_id = ? AND usage_date = CURRENT_DATE
                        """,
                (resultSet, rowNum) -> new TokenUsage(
                        resultSet.getString("user_id"),
                        resultSet.getInt("tokens_spent")
                ),
                validatedUserId
        ).stream().findFirst().orElse(new TokenUsage(validatedUserId, 0));
    }

    private String validateUserId(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return userId;
    }
}
