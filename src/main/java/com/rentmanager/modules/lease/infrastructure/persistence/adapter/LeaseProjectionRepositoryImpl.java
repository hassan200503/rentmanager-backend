package com.rentmanager.modules.lease.infrastructure.persistence.adapter;

import com.rentmanager.modules.lease.application.query.projection.LeaseProjection;
import com.rentmanager.modules.lease.application.query.projection.LeaseProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SaaS-grade JDBC projection repository implementation.
 *
 * READ-ONLY QUERY ADAPTER
 * - No business logic
 * - No domain orchestration
 * - Strict tenant isolation
 * - CQRS-compatible
 */
@Repository
@RequiredArgsConstructor
public class LeaseProjectionRepositoryImpl
        implements LeaseProjectionRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LeaseProjection> ROW_MAPPER =
            new LeaseProjectionRowMapper();

    @Override
    public Optional<LeaseProjection> findById(
            UUID tenantId,
            UUID leaseId
    ) {

        String sql = """
                SELECT *
                FROM leases
                WHERE tenant_id = ?
                  AND id = ?
                """;

        List<LeaseProjection> results = jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                tenantId,
                leaseId
        );

        return results.stream().findFirst();
    }

    @Override
    public List<LeaseProjection> findByTenant(
            UUID tenantId
    ) {

        String sql = """
                SELECT *
                FROM leases
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                tenantId
        );
    }

    @Override
    public List<LeaseProjection> findByProperty(
            UUID tenantId,
            UUID propertyId
    ) {

        String sql = """
                SELECT *
                FROM leases
                WHERE tenant_id = ?
                  AND property_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                tenantId,
                propertyId
        );
    }

    @Override
    public List<LeaseProjection> findByUnit(
            UUID tenantId,
            UUID unitId
    ) {

        String sql = """
                SELECT *
                FROM leases
                WHERE tenant_id = ?
                  AND unit_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                tenantId,
                unitId
        );
    }

    /**
     * Internal SaaS-grade projection mapper.
     */
    private static final class LeaseProjectionRowMapper
            implements RowMapper<LeaseProjection> {

        @Override
        public LeaseProjection mapRow(
                ResultSet rs,
                int rowNum
        ) throws SQLException {

            LeaseProjection projection = new LeaseProjection();

            projection.setLeaseId(
                    rs.getObject("id", UUID.class)
            );

            projection.setTenantId(
                    rs.getObject("tenant_id", UUID.class)
            );

            projection.setPropertyId(
                    rs.getObject("property_id", UUID.class)
            );

            projection.setUnitId(
                    rs.getObject("unit_id", UUID.class)
            );

            projection.setTenantProfileId(
                    rs.getObject("tenant_profile_id", UUID.class)
            );

            projection.setStatus(
                    rs.getString("status")
            );

            projection.setRentAmount(
                    rs.getBigDecimal("rent_amount")
            );

            projection.setSecurityDeposit(
                    rs.getBigDecimal("security_deposit")
            );

            projection.setStartDate(
                    toInstant(rs.getTimestamp("start_date"))
            );

            projection.setEndDate(
                    toInstant(rs.getTimestamp("end_date"))
            );

            projection.setCreatedAt(
                    toInstant(rs.getTimestamp("created_at"))
            );

            projection.setUpdatedAt(
                    toInstant(rs.getTimestamp("updated_at"))
            );

            return projection;
        }

        private static Instant toInstant(
                Timestamp timestamp
        ) {

            return timestamp != null
                    ? timestamp.toInstant()
                    : null;
        }
    }
}