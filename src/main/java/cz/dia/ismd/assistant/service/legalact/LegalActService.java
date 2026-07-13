package cz.dia.ismd.assistant.service.legalact;

import cz.dia.ismd.assistant.data.legalact.LegalAct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LegalActService {

    private static final String COLUMNS = "id, number, year, date, title, created_at, updated_at";
    private static final RowMapper<LegalAct> ROW_MAPPER = (resultSet, rowNum) -> new LegalAct(
            resultSet.getLong("id"),
            resultSet.getInt("number"),
            Year.of(resultSet.getInt("year")),
            resultSet.getObject("date", java.time.LocalDate.class),
            resultSet.getString("title"),
            resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
            resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public LegalActService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LegalAct create(LegalAct legalAct) {
        Objects.requireNonNull(legalAct, "legalAct must not be null");
        Objects.requireNonNull(legalAct.year(), "legalAct.year must not be null");
        Objects.requireNonNull(legalAct.date(), "legalAct.date must not be null");
        Objects.requireNonNull(legalAct.title(), "legalAct.title must not be null");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO legal_acts(number, year, date, title)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setInt(1, legalAct.number());
            statement.setInt(2, legalAct.year().getValue());
            statement.setObject(3, legalAct.date());
            statement.setString(4, legalAct.title());
            return statement;
        }, keyHolder);

        long id = Objects.requireNonNull(keyHolder.getKey(), "Database did not return a legal act id").longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("Created legal act was not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<LegalAct> findById(long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM legal_acts WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<LegalAct> find(int number, Year year, java.time.LocalDate date) {
        Objects.requireNonNull(year, "year must not be null");
        Objects.requireNonNull(date, "date must not be null");
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM legal_acts
                        WHERE number = ? AND year = ? AND date = ?
                        ORDER BY id
                        LIMIT 1
                        """.formatted(COLUMNS), ROW_MAPPER, number, year.getValue(), date)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<LegalAct> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM legal_acts ORDER BY id", ROW_MAPPER);
    }

    @Transactional
    public Optional<LegalAct> update(long id, LegalAct legalAct) {
        Objects.requireNonNull(legalAct, "legalAct must not be null");
        Objects.requireNonNull(legalAct.year(), "legalAct.year must not be null");
        Objects.requireNonNull(legalAct.date(), "legalAct.date must not be null");
        Objects.requireNonNull(legalAct.title(), "legalAct.title must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE legal_acts
                SET number = ?, year = ?, date = ?, title = ?, updated_at = now()
                WHERE id = ?
                """, legalAct.number(), legalAct.year().getValue(), legalAct.date(), legalAct.title(), id);
        return updated == 0 ? Optional.empty() : findById(id);
    }

    @Transactional
    public boolean delete(long id) {
        return jdbcTemplate.update("DELETE FROM legal_acts WHERE id = ?", id) > 0;
    }
}
