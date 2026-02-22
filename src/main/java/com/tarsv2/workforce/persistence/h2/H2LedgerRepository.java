package com.tarsv2.workforce.persistence.h2;

import com.tarsv2.workforce.economics.CostLedger;
import com.tarsv2.workforce.economics.RevenueLedger;
import com.tarsv2.workforce.persistence.LedgerRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class H2LedgerRepository implements LedgerRepository {
    private final DataSource dataSource;

    public H2LedgerRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public void save(CostLedger e) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO cost_ledger (id,task_id,model_id,provider,input_tokens,output_tokens,api_calls,cost_usd,recorded_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setObject(1, e.id()); ps.setObject(2, e.taskId()); ps.setString(3, e.modelId()); ps.setString(4, e.provider());
            ps.setInt(5, e.inputTokens()); ps.setInt(6, e.outputTokens()); ps.setInt(7, e.apiCalls()); ps.setDouble(8, e.costUsd());
            ps.setTimestamp(9, Timestamp.from(e.recordedAt())); ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public void save(RevenueLedger e) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO revenue_ledger (id,task_id,revenue_source,description,amount_usd,confirmed,recorded_at,confirmed_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setObject(1, e.id()); ps.setObject(2, e.taskId()); ps.setString(3, e.revenueSource()); ps.setString(4, e.description());
            ps.setDouble(5, e.amountUsd()); ps.setBoolean(6, e.confirmed()); ps.setTimestamp(7, Timestamp.from(e.recordedAt()));
            ps.setTimestamp(8, e.confirmedAt() == null ? null : Timestamp.from(e.confirmedAt())); ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public Optional<CostLedger> findCostById(UUID id) { return findCost("SELECT * FROM cost_ledger WHERE id=?", id); }
    @Override public Optional<RevenueLedger> findRevenueById(UUID id) { return findRevenue("SELECT * FROM revenue_ledger WHERE id=?", id); }
    @Override public List<CostLedger> findAllCosts() { return findCosts("SELECT * FROM cost_ledger"); }
    @Override public List<RevenueLedger> findAllRevenue() { return findRevenues("SELECT * FROM revenue_ledger"); }
    @Override public List<CostLedger> findCostsByTaskId(UUID taskId) { return findCosts("SELECT * FROM cost_ledger WHERE task_id=?", taskId); }
    @Override public List<RevenueLedger> findRevenuesByTaskId(UUID taskId) { return findRevenues("SELECT * FROM revenue_ledger WHERE task_id=?", taskId); }

    @Override public void update(CostLedger e) { deleteCost(e.id()); save(e); }
    @Override public void update(RevenueLedger e) { deleteRevenue(e.id()); save(e); }

    @Override public void deleteCost(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM cost_ledger WHERE id=?")) {
            ps.setObject(1, id); ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public void deleteRevenue(UUID id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM revenue_ledger WHERE id=?")) {
            ps.setObject(1, id); ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public double totalCost() { return sum("SELECT COALESCE(SUM(cost_usd),0) FROM cost_ledger"); }
    @Override public double totalRevenue() { return sum("SELECT COALESCE(SUM(amount_usd),0) FROM revenue_ledger"); }

    private Optional<CostLedger> findCost(String sql, UUID id) { List<CostLedger> l = findCosts(sql, id); return l.isEmpty()?Optional.empty():Optional.of(l.getFirst()); }
    private Optional<RevenueLedger> findRevenue(String sql, UUID id) { List<RevenueLedger> l = findRevenues(sql, id); return l.isEmpty()?Optional.empty():Optional.of(l.getFirst()); }
    private double sum(String sql) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) { return rs.next()?rs.getDouble(1):0d; }
        catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private List<CostLedger> findCosts(String sql, UUID... id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (id.length > 0) ps.setObject(1, id[0]);
            try (ResultSet rs = ps.executeQuery()) {
                List<CostLedger> out = new ArrayList<>();
                while (rs.next()) out.add(new CostLedger((UUID) rs.getObject("id"), (UUID) rs.getObject("task_id"), rs.getString("model_id"),
                        rs.getString("provider"), rs.getInt("input_tokens"), rs.getInt("output_tokens"), rs.getInt("api_calls"),
                        rs.getDouble("cost_usd"), rs.getTimestamp("recorded_at").toInstant()));
                return out;
            }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private List<RevenueLedger> findRevenues(String sql, UUID... id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (id.length > 0) ps.setObject(1, id[0]);
            try (ResultSet rs = ps.executeQuery()) {
                List<RevenueLedger> out = new ArrayList<>();
                while (rs.next()) {
                    Timestamp confirmed = rs.getTimestamp("confirmed_at");
                    out.add(new RevenueLedger((UUID) rs.getObject("id"), (UUID) rs.getObject("task_id"), rs.getString("revenue_source"),
                            rs.getString("description"), rs.getDouble("amount_usd"), rs.getBoolean("confirmed"),
                            rs.getTimestamp("recorded_at").toInstant(), confirmed == null ? null : confirmed.toInstant()));
                }
                return out;
            }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }
}
