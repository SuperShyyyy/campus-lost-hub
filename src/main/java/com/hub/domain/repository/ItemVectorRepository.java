package com.hub.domain.repository;

import com.hub.domain.po.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Repository
public class ItemVectorRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void updateItemEmbedding(long itemId, float[] vector) {
        String sql = """
            UPDATE item
            SET embedding = CAST(? AS vector)
            WHERE id = ?
        """;
        jdbcTemplate.update(sql, formatVector(vector), itemId);
    }

    public List<ItemSearchHit> search(float[] vector, double minScore, int limit, int offset) {
        String sql = """
            SELECT id,
                   user_id,
                   type,
                   title,
                   description,
                   location,
                   image_url,
                   status,
                   created_at,
                   updated_at,
                   embedding <=> CAST(? AS vector) AS distance,
                   1 - (embedding <=> CAST(? AS vector)) AS score
            FROM item
            WHERE embedding IS NOT NULL
              AND 1 - (embedding <=> CAST(? AS vector)) >= ?
            ORDER BY distance ASC, id DESC
            LIMIT ?
            OFFSET ?
        """;

        return jdbcTemplate.query(sql,
                ps -> {
                    String vectorLiteral = formatVector(vector);
                    ps.setString(1, vectorLiteral);
                    ps.setString(2, vectorLiteral);
                    ps.setString(3, vectorLiteral);
                    ps.setDouble(4, minScore);
                    ps.setInt(5, limit);
                    ps.setInt(6, offset);
                },
                (rs, i) -> new ItemSearchHit(
                        mapItem(rs),
                        rs.getDouble("score"),
                        rs.getDouble("distance")
                ));
    }

    public long count(float[] vector, double minScore) {
        String sql = """
            SELECT COUNT(*)
            FROM item
            WHERE embedding IS NOT NULL
              AND 1 - (embedding <=> CAST(? AS vector)) >= ?
        """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, formatVector(vector), minScore);
        return count == null ? 0L : count;
    }

    private Item mapItem(ResultSet rs) throws SQLException {
        Item item = new Item();

        item.setId(rs.getLong("id"));
        item.setUserId(rs.getLong("user_id"));
        item.setType(rs.getInt("type"));
        item.setTitle(rs.getString("title"));
        item.setDescription(rs.getString("description"));
        item.setLocation(rs.getString("location"));
        item.setImageUrl(rs.getString("image_url"));
        item.setStatus(rs.getInt("status"));

        item.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        item.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return item;
    }

    private String formatVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US, "%.8f", vector[i]));
        }
        builder.append(']');
        return builder.toString();
    }
}