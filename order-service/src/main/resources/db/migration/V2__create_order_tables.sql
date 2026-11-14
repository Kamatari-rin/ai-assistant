CREATE TABLE orders.orders (
                               id UUID NOT NULL,
                               display_number TEXT NOT NULL,
                               status TEXT NOT NULL,
                               delay_reason TEXT NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL,
                               updated_at TIMESTAMPTZ NOT NULL,

                               CONSTRAINT pk_orders
                                   PRIMARY KEY (id),

                               CONSTRAINT uq_orders_display_number
                                   UNIQUE (display_number),

                               CONSTRAINT ck_orders_display_number_not_empty
                                   CHECK (char_length(display_number) > 0),

                               CONSTRAINT ck_orders_status
                                   CHECK (status IN ('DELAYED')),

                               CONSTRAINT ck_orders_delay_reason
                                   CHECK (delay_reason IN ('OUT_OF_STOCK')),

                               CONSTRAINT ck_orders_timestamps
                                   CHECK (updated_at >= created_at)
);


CREATE TABLE orders.order_events (
                                     id UUID NOT NULL,
                                     order_id UUID NOT NULL,
                                     type TEXT NOT NULL,
                                     description TEXT NOT NULL,
                                     created_at TIMESTAMPTZ NOT NULL,

                                     CONSTRAINT pk_order_events
                                         PRIMARY KEY (id),

                                     CONSTRAINT fk_order_events_order
                                         FOREIGN KEY (order_id)
                                             REFERENCES orders.orders (id)
                                             ON DELETE RESTRICT
);


CREATE INDEX idx_order_events_order_created_id
    ON orders.order_events (order_id, created_at, id);


CREATE TABLE orders.escalations (
                                    id UUID NOT NULL,
                                    order_id UUID NOT NULL,
                                    order_display_number TEXT NOT NULL,
                                    reason TEXT NOT NULL,
                                    priority TEXT NOT NULL,
                                    idempotency_key UUID NOT NULL,
                                    created_at TIMESTAMPTZ NOT NULL,

                                    CONSTRAINT pk_escalations
                                        PRIMARY KEY (id),

                                    CONSTRAINT fk_escalations_order
                                        FOREIGN KEY (order_id)
                                            REFERENCES orders.orders (id)
                                            ON DELETE RESTRICT,

                                    CONSTRAINT uq_escalations_idempotency_key
                                        UNIQUE (idempotency_key),

                                    CONSTRAINT ck_escalations_order_display_number_not_empty
                                        CHECK (char_length(order_display_number) > 0),

                                    CONSTRAINT ck_escalations_reason
                                        CHECK (reason IN ('OUT_OF_STOCK')),

                                    CONSTRAINT ck_escalations_priority
                                        CHECK (priority IN ('HIGH'))
);


CREATE INDEX idx_escalations_order_id
    ON orders.escalations (order_id);
