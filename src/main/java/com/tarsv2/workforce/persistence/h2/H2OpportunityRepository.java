package com.tarsv2.workforce.persistence.h2;

import com.tarsv2.workforce.persistence.OpportunityRepository;
import com.tarsv2.workforce.revenue.Opportunity;
import com.tarsv2.workforce.revenue.OpportunityStatus;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class H2OpportunityRepository implements OpportunityRepository {

    private final DataSource dataSource;

    public H2OpportunityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Opportunity e) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO opportunities (id,title,description,source,status,estimated_value,estimated_cost,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            bind(ps, e);
            ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override
    public Optional<Opportunity> findById(UUID id) {
        List<Opportunity> list = query("SELECT * FROM opportunities WHERE id=?", id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Opportunity> findAll() {
        return query("SELECT * FROM opportunities");
    }

    @Override
    public List<Opportunity> findByStatus(OpportunityStatus status) {
        return query("SELECT * FROM opportunities WHERE status=?", status.name());
    }

    @Override
    public void update(Opportunity e) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE opportunities SET title=?,description=?,source=?,status=?,estimated_value=?,estimated_cost=?,created_at=?,updated_at=? WHERE id=?")) {
            ps.setString(1, e.title());
            ps.setString(2, e.description());
            ps.setString(3, e.source());
            ps.setString(4, e.status().name());
            ps.setDouble(5, e.estimatedValue());
            ps.setDouble(6, e.estimatedCost());
            ps.setTimestamp(7, Timestamp.from(e.createdAt()));
            ps.setTimestamp(8, Timestamp.from(e.updatedAt()));
            ps.setObject(9, e.id());
            ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override
    public void delete(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM opportunities WHERE id=?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private void bind(PreparedStatement ps, Opportunity e) throws SQLException {
        ps.setObject(1, e.id());
        ps.setString(2, e.title());
        ps.setString(3, e.description());
        ps.setString(4, e.source());
        ps.setString(5, e.status().name());
        ps.setDouble(6, e.estimatedValue());
        ps.setDouble(7, e.estimatedCost());
        ps.setTimestamp(8, Timestamp.from(e.createdAt()));
        ps.setTimestamp(9, Timestamp.from(e.updatedAt()));
    }

    private List<Opportunity> query(String sql, Object... args) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Opportunity> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Opportunity((UUID) rs.getObject("id"), rs.getString("title"), rs.getString("description"),
                            rs.getString("source"), OpportunityStatus.valueOf(rs.getString("status")), rs.getDouble("estimated_value"),
                            rs.getDouble("estimated_cost"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                }
                return out;
            }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }
}
