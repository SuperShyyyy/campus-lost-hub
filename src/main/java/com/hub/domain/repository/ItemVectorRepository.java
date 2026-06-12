package com.hub.domain.repository;

import com.hub.domain.po.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

@Repository
public class ItemVectorRepository {

    private static final String TEXT_COLUMN = "text_embedding";
    private static final String IMAGE_COLUMN = "image_embedding";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void updateTextEmbedding(long itemId, float[] vector) {
        updateEmbedding(itemId, vector, TEXT_COLUMN);
    }

    public void updateImageEmbedding(long itemId, float[] vector) {
        updateEmbedding(itemId, vector, IMAGE_COLUMN);
    }

    public List<ItemSearchHit> searchByText(float[] vector, double minScore, int limit, int offset) {
        return search(vector, minScore, limit, offset, TEXT_COLUMN);
    }

    public List<ItemSearchHit> searchByImage(float[] vector, double minScore, int limit, int offset) {
        return search(vector, minScore, limit, offset, IMAGE_COLUMN);
    }

    public long countByText(float[] vector, double minScore) {
        return count(vector, minScore, TEXT_COLUMN);
    }

    public long countByImage(float[] vector, double minScore) {
        return count(vector, minScore, IMAGE_COLUMN);
    }

    private void updateEmbedding(long itemId, float[] vector, String column) {
        String sql = """
            UPDATE item
            SET %s = CAST(? AS vector)
            WHERE id = ?
            """.formatted(column);
        jdbcTemplate.update(sql, formatVector(vector), itemId);
    }

    private List<ItemSearchHit> search(float[] vector, double minScore, int limit, int offset, String column) {
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
                   %s <=> CAST(? AS vector) AS distance,
                   1 - (%s <=> CAST(? AS vector)) AS score
            FROM item
            WHERE %s IS NOT NULL
              AND 1 - (%s <=> CAST(? AS vector)) >= ?
            ORDER BY distance ASC, id DESC
            LIMIT ?
            OFFSET ?
            """.formatted(column, column, column, column);

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

    private long count(float[] vector, double minScore, String column) {
        // 子查询 LIMIT 21：pgvector 只对前 21 个命中行计算余弦距离，
        // 避免 COUNT(*) 对所有 image_embedding IS NOT NULL 的行计算距离。
        String sql = """
            SELECT COUNT(*) FROM (
                SELECT 1
                FROM item
                WHERE %s IS NOT NULL
                  AND 1 - (%s <=> CAST(? AS vector)) >= ?
                LIMIT 21
            ) sub
            """.formatted(column, column);
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

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            item.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            item.setUpdatedAt(updatedAt.toLocalDateTime());
        }
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
