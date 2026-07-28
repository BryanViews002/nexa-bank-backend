package com.example.bank;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KycSchemaMigrationTest {

    @Test
    void addsReviewColumnsWithoutLosingExistingDocuments() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE kyc_documents (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        uploaded_at DATETIME(6),
                        user_id BIGINT NOT NULL,
                        content_type VARCHAR(255),
                        filename VARCHAR(255),
                        path VARCHAR(255),
                        status ENUM ('APPROVED', 'PENDING', 'REJECTED'),
                        PRIMARY KEY (id)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO kyc_documents
                        (uploaded_at, user_id, content_type, filename, path, status)
                    VALUES
                        (CURRENT_TIMESTAMP, 42, 'application/pdf',
                         'identity.pdf', 'uploads/42/identity.pdf', 'PENDING')
                    """);

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V7__add_kyc_review_columns.sql")
            );

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT rejection_reason, reviewed_at, reviewed_by_user_id
                    FROM kyc_documents
                    WHERE id = 1
                    """)) {
                resultSet.next();
                assertNull(resultSet.getString("rejection_reason"));
                assertNull(resultSet.getTimestamp("reviewed_at"));
                assertNull(resultSet.getObject("reviewed_by_user_id"));
            }

            statement.executeUpdate("""
                    UPDATE kyc_documents
                    SET status = 'REJECTED',
                        rejection_reason = 'Document is unreadable',
                        reviewed_at = CURRENT_TIMESTAMP,
                        reviewed_by_user_id = 7
                    WHERE id = 1
                    """);

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT status, rejection_reason, reviewed_by_user_id
                    FROM kyc_documents
                    WHERE id = 1
                    """)) {
                resultSet.next();
                assertEquals("REJECTED", resultSet.getString("status"));
                assertEquals("Document is unreadable", resultSet.getString("rejection_reason"));
                assertEquals(7L, resultSet.getLong("reviewed_by_user_id"));
            }
        }
    }

    private String jdbcUrl() {
        return "jdbc:h2:mem:kyc_migration"
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }
}
