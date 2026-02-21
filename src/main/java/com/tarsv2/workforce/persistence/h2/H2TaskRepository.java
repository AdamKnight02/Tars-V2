package com.tarsv2.workforce.persistence.h2;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.persistence.TaskRepository;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskPriority;
import com.tarsv2.workforce.task.TaskStatus;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class H2TaskRepository implements TaskRepository {

    private final DataSource dataSource;

    public H2TaskRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Task e) {
        String sql = "INSERT INTO tasks (id,title,description,assigned_role,status,priority,parent_task_id,goal_origin,estimated_cost,expected_revenue,retry_count,max_retries,created_at,updated_at,completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, e);
            ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override
    public Optional<Task> findById(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM tasks WHERE id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override
    public List<Task> findAll() { return query("SELECT * FROM tasks"); }

    @Override
    public List<Task> findByStatus(TaskStatus status) { return queryWithArg("SELECT * FROM tasks WHERE status=?", status.name()); }

    @Override
    public List<Task> findByGoalOrigin(String goalOrigin) { return queryWithArg("SELECT * FROM tasks WHERE goal_origin=?", goalOrigin); }

    @Override
    public void update(Task e) {
        String sql = "UPDATE tasks SET title=?,description=?,assigned_role=?,status=?,priority=?,parent_task_id=?,goal_origin=?,estimated_cost=?,expected_revenue=?,retry_count=?,max_retries=?,created_at=?,updated_at=?,completed_at=? WHERE id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.title());
            ps.setString(2, e.description());
            ps.setString(3, e.assignedRole().name());
            ps.setString(4, e.status().name());
            ps.setString(5, e.priority().name());
            ps.setObject(6, e.parentTaskId());
            ps.setString(7, e.goalOrigin());
            ps.setDouble(8, e.estimatedCostUsd());
            ps.setDouble(9, e.expectedRevenueUsd());
            ps.setInt(10, e.retryCount());
            ps.setInt(11, e.maxRetries());
            ps.setTimestamp(12, Timestamp.from(e.createdAt()));
            ps.setTimestamp(13, Timestamp.from(e.updatedAt()));
            ps.setTimestamp(14, e.completedAt() == null ? null : Timestamp.from(e.completedAt()));
            ps.setObject(15, e.id());
            ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override
    public void delete(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM tasks WHERE id=?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private void bind(PreparedStatement ps, Task e) throws SQLException {
        ps.setObject(1, e.id());
        ps.setString(2, e.title());
        ps.setString(3, e.description());
        ps.setString(4, e.assignedRole().name());
        ps.setString(5, e.status().name());
        ps.setString(6, e.priority().name());
        ps.setObject(7, e.parentTaskId());
        ps.setString(8, e.goalOrigin());
        ps.setDouble(9, e.estimatedCostUsd());
        ps.setDouble(10, e.expectedRevenueUsd());
        ps.setInt(11, e.retryCount());
        ps.setInt(12, e.maxRetries());
        ps.setTimestamp(13, Timestamp.from(e.createdAt()));
        ps.setTimestamp(14, Timestamp.from(e.updatedAt()));
        ps.setTimestamp(15, e.completedAt() == null ? null : Timestamp.from(e.completedAt()));
    }

    private List<Task> query(String sql) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Task> out = new ArrayList<>(); while (rs.next()) out.add(map(rs)); return out;
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private List<Task> queryWithArg(String sql, String arg) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> out = new ArrayList<>(); while (rs.next()) out.add(map(rs)); return out;
            }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private Task map(ResultSet rs) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new Task((UUID) rs.getObject("id"), rs.getString("title"), rs.getString("description"),
                AgentRole.valueOf(rs.getString("assigned_role")), TaskStatus.valueOf(rs.getString("status")),
                TaskPriority.valueOf(rs.getString("priority")), (UUID) rs.getObject("parent_task_id"), rs.getString("goal_origin"),
                rs.getDouble("estimated_cost"), rs.getDouble("expected_revenue"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), completed == null ? null : completed.toInstant(),
                rs.getInt("retry_count"), rs.getInt("max_retries"));
    }
}
