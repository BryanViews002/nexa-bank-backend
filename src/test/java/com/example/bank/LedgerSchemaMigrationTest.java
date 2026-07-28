package com.example.bank;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerSchemaMigrationTest {

    @Test
    void alignsV2LedgerTablesWithCurrentEntities() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("success"), "sa", "");
             Statement statement = connection.createStatement()) {
            createV2LedgerTables(statement);
            statement.executeUpdate("""
                    INSERT INTO ledger_journals (reference, description, created_at)
                    VALUES ('legacy-journal', NULL, CURRENT_TIMESTAMP)
                    """);

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V6__align_ledger_schema.sql")
            );

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT event_type, description, status
                    FROM ledger_journals
                    WHERE reference = 'legacy-journal'
                    """)) {
                resultSet.next();
                assertEquals("LEGACY", resultSet.getString("event_type"));
                assertEquals("Legacy ledger journal", resultSet.getString("description"));
                assertEquals("POSTED", resultSet.getString("status"));
            }

            statement.executeUpdate("INSERT INTO transactions DEFAULT VALUES");
            statement.executeUpdate("""
                    INSERT INTO ledger_journals
                        (reference, transaction_id, event_type, description, status, created_at)
                    VALUES
                        ('new-journal', 1, 'ACCOUNT_OPENING_BONUS',
                         'Account opening bonus', 'POSTED', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO ledger_postings
                        (journal_id, ledger_account_id, direction, amount, currency, created_at)
                    VALUES
                        (2, 1, 'DEBIT', 25.00, 'USD', CURRENT_TIMESTAMP),
                        (2, 2, 'CREDIT', 25.00, 'USD', CURRENT_TIMESTAMP)
                    """);

            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO ledger_postings
                        (journal_id, ledger_account_id, direction, amount, currency, created_at)
                    VALUES
                        (2, 1, 'INVALID', 25.00, 'USD', CURRENT_TIMESTAMP)
                    """));
        }
    }

    @Test
    void rejectsAmbiguousLegacyPostings() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("legacy_posting"), "sa", "");
             Statement statement = connection.createStatement()) {
            createV2LedgerTables(statement);
            statement.executeUpdate("""
                    INSERT INTO ledger_postings
                        (journal_id, ledger_account_id, amount, currency, created_at)
                    VALUES
                        (1, 1, 25.00, 'USD', CURRENT_TIMESTAMP)
                    """);

            assertThrows(ScriptStatementFailedException.class, () -> ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V6__align_ledger_schema.sql")
            ));

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_name = 'ledger_journals'
                      AND column_name = 'event_type'
                    """)) {
                resultSet.next();
                assertEquals(0, resultSet.getInt(1));
            }
        }
    }

    private void createV2LedgerTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE transactions (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    PRIMARY KEY (id)
                )
                """);
        statement.execute("""
                CREATE TABLE ledger_journals (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    reference VARCHAR(100) NOT NULL,
                    description VARCHAR(500),
                    created_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (id),
                    CONSTRAINT uk_ledger_journals_reference UNIQUE (reference)
                )
                """);
        statement.execute("""
                CREATE TABLE ledger_postings (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    journal_id BIGINT NOT NULL,
                    ledger_account_id BIGINT NOT NULL,
                    amount DECIMAL(19,4) NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (id)
                )
                """);
    }

    private String jdbcUrl(String databaseName) {
        return "jdbc:h2:mem:ledger_migration_" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }
}
