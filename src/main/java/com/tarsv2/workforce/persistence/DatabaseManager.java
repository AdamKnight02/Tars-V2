package com.tarsv2.workforce.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final HikariDataSource dataSource;

    public DatabaseManager() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:file:./tars-workforce;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        config.setPoolName("tars-workforce-pool");
        this.dataSource = new HikariDataSource(config);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void initialize() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        id UUID PRIMARY KEY,
                        title VARCHAR(500) NOT NULL,
                        description TEXT,
                        assigned_role VARCHAR(20) NOT NULL,
                        status VARCHAR(30) NOT NULL,
                        priority VARCHAR(10) NOT NULL,
                        parent_task_id UUID,
                        goal_origin TEXT,
                        estimated_cost DECIMAL(10,4),
                        expected_revenue DECIMAL(10,4),
                        retry_count INT DEFAULT 0,
                        max_retries INT DEFAULT 2,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        completed_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cost_ledger (
                        id UUID PRIMARY KEY,
                        task_id UUID,
                        model_id VARCHAR(50) NOT NULL,
                        provider VARCHAR(50) NOT NULL,
                        input_tokens INT NOT NULL,
                        output_tokens INT NOT NULL,
                        api_calls INT NOT NULL,
                        cost_usd DECIMAL(10,6) NOT NULL,
                        recorded_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS revenue_ledger (
                        id UUID PRIMARY KEY,
                        task_id UUID,
                        revenue_source VARCHAR(200),
                        description TEXT,
                        amount_usd DECIMAL(10,4) NOT NULL,
                        confirmed BOOLEAN DEFAULT FALSE,
                        recorded_at TIMESTAMP NOT NULL,
                        confirmed_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS opportunities (
                        id UUID PRIMARY KEY,
                        title VARCHAR(500) NOT NULL,
                        description TEXT,
                        source VARCHAR(200),
                        status VARCHAR(20) NOT NULL,
                        estimated_value DECIMAL(10,4),
                        estimated_cost DECIMAL(10,4),
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS execution_log (
                        id UUID PRIMARY KEY,
                        task_id UUID,
                        agent_name VARCHAR(100),
                        event VARCHAR(50) NOT NULL,
                        detail TEXT,
                        timestamp TIMESTAMP NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize workforce DB", e);
        }
    }
}
