package com.rentmanager.modules.notification.push.infrastructure;

import com.rentmanager.modules.notification.push.domain.PushTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Plain JDBC: the table is a short-lived work queue with no domain behaviour,
 * so a JPA entity would add ceremony without adding safety.
 */
@Component
@RequiredArgsConstructor
public class PushTicketJdbcRepository implements PushTicketRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void save(String ticketId, String pushToken) {
        jdbc.update(
                "INSERT INTO push_tickets (ticket_id, push_token, created_at) VALUES (?, ?, ?) ON CONFLICT (ticket_id) DO NOTHING",
                ticketId, pushToken, Timestamp.from(Instant.now()));
    }

    @Override
    public List<PendingTicket> findCreatedBefore(Instant cutoff, int limit) {
        return jdbc.query(
                "SELECT ticket_id, push_token, created_at FROM push_tickets WHERE created_at <= ? ORDER BY created_at LIMIT ?",
                (rs, i) -> new PendingTicket(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
                Timestamp.from(cutoff), limit);
    }

    @Override
    @Transactional
    public void deleteAll(Collection<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("DELETE FROM push_tickets WHERE ticket_id = ?",
                ticketIds.stream().map(id -> new Object[]{id}).toList());
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM push_tickets WHERE created_at < ?", Timestamp.from(cutoff));
    }
}
