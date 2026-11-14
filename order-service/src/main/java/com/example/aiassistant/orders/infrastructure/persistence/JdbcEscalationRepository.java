
package com.example.aiassistant.orders.infrastructure.persistence;

import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEscalationRepository
        implements EscalationRepository {

    private static final String FIND_BY_IDEMPOTENCY_KEY = """
            SELECT
                id,
                order_id,
                order_display_number,
                reason,
                priority,
                idempotency_key,
                created_at
            FROM orders.escalations
            WHERE idempotency_key = :idempotencyKey
            """;

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO orders.escalations (
                id,
                order_id,
                order_display_number,
                reason,
                priority,
                idempotency_key,
                created_at
            )
            VALUES (
                :id,
                :orderId,
                :displayNumber,
                :reason,
                :priority,
                :idempotencyKey,
                :createdAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING
                id,
                order_id,
                order_display_number,
                reason,
                priority,
                idempotency_key,
                created_at
            """;

    private static final RowMapper<Escalation> ESCALATION_ROW_MAPPER =
            (resultSet, rowNum) -> new Escalation(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("order_id", UUID.class),
                    new EscalationIdentity(
                            resultSet.getString(
                                    "order_display_number"
                            ),
                            EscalationReason.valueOf(
                                    resultSet.getString("reason")
                            ),
                            EscalationPriority.valueOf(
                                    resultSet.getString("priority")
                            )
                    ),
                    resultSet.getObject(
                            "idempotency_key",
                            UUID.class
                    ),
                    resultSet.getObject(
                            "created_at",
                            OffsetDateTime.class
                    ).toInstant()
            );

    private final JdbcClient jdbcClient;

    public JdbcEscalationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Escalation> findByIdempotencyKey(
            UUID idempotencyKey
    ) {

        return jdbcClient.sql(FIND_BY_IDEMPOTENCY_KEY)
                .param("idempotencyKey", idempotencyKey)
                .query(ESCALATION_ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Escalation> insertIfAbsent(
            Escalation escalation
    ) {

        return jdbcClient.sql(INSERT_IF_ABSENT)
                .param("id", escalation.id())
                .param("orderId", escalation.orderId())
                .param(
                        "displayNumber",
                        escalation.identity().displayNumber()
                )
                .param(
                        "reason",
                        escalation.identity().reason().name()
                )
                .param(
                        "priority",
                        escalation.identity().priority().name()
                )
                .param(
                        "idempotencyKey",
                        escalation.idempotencyKey()
                )
                .param(
                        "createdAt",
                        escalation.createdAt()
                                .atOffset(ZoneOffset.UTC)
                )
                .query(ESCALATION_ROW_MAPPER)
                .optional();
    }
}
