package com.university.erp.service.admin;

import com.university.erp.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditService audit;

    public record Input(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String body,
        @Pattern(regexp = "ALL|STUDENTS|FACULTY|STAFF") String audience,
        @Pattern(regexp = "NORMAL|IMPORTANT|URGENT") String priority,
        Boolean pinned,
        OffsetDateTime publishAt,
        OffsetDateTime expiresAt) {}

    public record View(Long id, String title, String body, String audience, String priority, boolean pinned,
                       OffsetDateTime publishAt, OffsetDateTime expiresAt, String state, String createdBy,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record Page(List<View> content, long totalElements, int totalPages, int number) {}

    private static final String SELECT = """
        SELECT n.*, u.full_name AS author,
               CASE WHEN n.publish_at > NOW() THEN 'SCHEDULED'
                    WHEN n.expires_at IS NOT NULL AND n.expires_at <= NOW() THEN 'EXPIRED'
                    ELSE 'ACTIVE' END AS state
        FROM notices n LEFT JOIN users u ON u.id = n.created_by
        """;

    private static final RowMapper<View> MAPPER = (rs, i) -> new View(
        rs.getLong("id"), rs.getString("title"), rs.getString("body"), rs.getString("audience"),
        rs.getString("priority"), rs.getBoolean("pinned"),
        rs.getObject("publish_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
        rs.getString("state"), rs.getString("author"),
        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    /** state: ACTIVE | SCHEDULED | EXPIRED | "" (all) */
    @Transactional(readOnly = true)
    public Page list(String search, String audience, String state, int page) {
        int size = 20;
        var p = new MapSqlParameterSource("search", "%" + (search == null ? "" : search.trim().toLowerCase()) + "%")
            .addValue("audience", audience == null ? "" : audience)
            .addValue("state", state == null ? "" : state)
            .addValue("limit", size).addValue("offset", Math.max(page, 0) * size);
        String where = """
            WHERE (LOWER(title) LIKE :search OR LOWER(body) LIKE :search)
              AND (:audience = '' OR audience = :audience)
              AND (:state = '' OR state = :state)
            """;
        String base = "SELECT * FROM (" + SELECT + ") x " + where;
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + base + ") c", p, Long.class);
        List<View> rows = jdbc.query(base + " ORDER BY pinned DESC, publish_at DESC LIMIT :limit OFFSET :offset", p, MAPPER);
        return new Page(rows, total, (int) Math.ceil(total / (double) size), Math.max(page, 0));
    }

    /** Currently visible notices for a reader role (used by student/faculty portals). */
    @Transactional(readOnly = true)
    public List<View> visibleFor(User.Role role) {
        String audience = switch (role) {
            case STUDENT -> "STUDENTS";
            case FACULTY -> "FACULTY";
            default -> "STAFF";
        };
        return jdbc.query("SELECT * FROM (" + SELECT + ") x WHERE state = 'ACTIVE' AND audience IN ('ALL', :aud) "
            + "ORDER BY pinned DESC, publish_at DESC LIMIT 50", new MapSqlParameterSource("aud", audience), MAPPER);
    }

    public View get(long id) {
        try {
            return jdbc.queryForObject(SELECT + " WHERE n.id = :id", new MapSqlParameterSource("id", id), MAPPER);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("Notice not found: " + id);
        }
    }

    private MapSqlParameterSource params(Input in) {
        OffsetDateTime publish = in.publishAt() != null ? in.publishAt() : OffsetDateTime.now();
        if (in.expiresAt() != null && !in.expiresAt().isAfter(publish))
            throw new IllegalArgumentException("Expiry must be after the publish time");
        return new MapSqlParameterSource("title", in.title().trim())
            .addValue("body", in.body().trim())
            .addValue("audience", in.audience() == null ? "ALL" : in.audience())
            .addValue("priority", in.priority() == null ? "NORMAL" : in.priority())
            .addValue("pinned", Boolean.TRUE.equals(in.pinned()))
            .addValue("publishAt", publish)
            .addValue("expiresAt", in.expiresAt());
    }

    @Transactional
    public View create(User actor, Input in) {
        var p = params(in).addValue("by", actor.getId());
        Long id = jdbc.queryForObject("""
            INSERT INTO notices (title, body, audience, priority, pinned, publish_at, expires_at, created_by)
            VALUES (:title, :body, :audience, :priority, :pinned, :publishAt, :expiresAt, :by) RETURNING id
            """, p, Long.class);
        audit.record(actor, "NOTICE_CREATED", "Notice", id);
        return get(id);
    }

    @Transactional
    public View update(User actor, long id, Input in) {
        int n = jdbc.update("""
            UPDATE notices SET title = :title, body = :body, audience = :audience, priority = :priority,
                   pinned = :pinned, publish_at = :publishAt, expires_at = :expiresAt, updated_at = NOW()
            WHERE id = :id
            """, params(in).addValue("id", id));
        if (n == 0) throw new NoSuchElementException("Notice not found: " + id);
        audit.record(actor, "NOTICE_UPDATED", "Notice", id);
        return get(id);
    }

    @Transactional
    public void delete(User actor, long id) {
        if (jdbc.update("DELETE FROM notices WHERE id = :id", new MapSqlParameterSource("id", id)) == 0)
            throw new NoSuchElementException("Notice not found: " + id);
        audit.record(actor, "NOTICE_DELETED", "Notice", id);
    }
}
