package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.model.legal.LegalActText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LegalActTextService {

    private static final String COLUMNS =
            "id, legal_act_id, legal_text, official_id, official_number, successor";
    private static final RowMapper<LegalActText> ROW_MAPPER = (resultSet, rowNum) -> new LegalActText(
            resultSet.getLong("id"),
            resultSet.getLong("legal_act_id"),
            resultSet.getString("legal_text"),
            resultSet.getString("official_id"),
            resultSet.getString("official_number"),
            resultSet.getObject("successor", Long.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public LegalActTextService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LegalActText create(LegalActText legalActText) {
        requireFields(legalActText);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO legal_act_texts(
                        legal_act_id, legal_text, official_id, official_number, successor
                    ) VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, legalActText.legalActId());
            statement.setString(2, legalActText.legalText());
            statement.setString(3, legalActText.officialId());
            statement.setString(4, legalActText.officialNumber());
            setNullableLong(statement, 5, legalActText.successorId());
            return statement;
        }, keyHolder);

        long id = Objects.requireNonNull(keyHolder.getKey(), "Database did not return a legal act text id").longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("Created legal act text was not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<LegalActText> findById(long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM legal_act_texts WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<LegalActText> findByLegalActId(long legalActId) {
        return jdbcTemplate.query("""
                SELECT %s
                FROM legal_act_texts
                WHERE legal_act_id = ?
                ORDER BY id
                """.formatted(COLUMNS), ROW_MAPPER, legalActId);
    }

    @Transactional(readOnly = true)
    public List<LegalActText> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM legal_act_texts ORDER BY id", ROW_MAPPER);
    }

    @Transactional
    public Optional<LegalActText> update(long id, LegalActText legalActText) {
        requireFields(legalActText);
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    UPDATE legal_act_texts
                    SET legal_act_id = ?, legal_text = ?, official_id = ?, official_number = ?, successor = ?
                    WHERE id = ?
                    """);
            statement.setLong(1, legalActText.legalActId());
            statement.setString(2, legalActText.legalText());
            statement.setString(3, legalActText.officialId());
            statement.setString(4, legalActText.officialNumber());
            setNullableLong(statement, 5, legalActText.successorId());
            statement.setLong(6, id);
            return statement;
        });
        return updated == 0 ? Optional.empty() : findById(id);
    }

    @Transactional
    public boolean delete(long id) {
        return jdbcTemplate.update("DELETE FROM legal_act_texts WHERE id = ?", id) > 0;
    }

    private static void requireFields(LegalActText legalActText) {
        Objects.requireNonNull(legalActText, "legalActText must not be null");
        Objects.requireNonNull(legalActText.legalText(), "legalActText.legalText must not be null");
        Objects.requireNonNull(legalActText.officialId(), "legalActText.officialId must not be null");
        Objects.requireNonNull(legalActText.officialNumber(), "legalActText.officialNumber must not be null");
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
