
INSERT INTO orders.orders (
    id,
    display_number,
    status,
    delay_reason,
    created_at,
    updated_at
)
VALUES
    (
        '09b98c5b-37a5-4fa3-97c7-a016d7517760',
        '12345',
        'DELAYED',
        'OUT_OF_STOCK',
        '2026-11-10T09:00:00Z',
        '2026-11-11T10:30:00Z'
    ),
    (
        '09b98c5b-37a5-4fa3-97c7-a016d7517761',
        '12346',
        'DELAYED',
        'OUT_OF_STOCK',
        '2026-11-10T11:00:00Z',
        '2026-11-11T12:00:00Z'
    ),
    (
        '09b98c5b-37a5-4fa3-97c7-a016d7517762',
        '12347',
        'DELAYED',
        'OUT_OF_STOCK',
        '2026-11-10T13:00:00Z',
        '2026-11-11T14:00:00Z'
    );


INSERT INTO orders.order_events (
    id,
    order_id,
    type,
    description,
    created_at
)
VALUES
    (
        'a1000000-0000-4000-8000-000000000001',
        '09b98c5b-37a5-4fa3-97c7-a016d7517760',
        'ORDER_CREATED',
        'Synthetic order was created.',
        '2026-11-10T09:00:00Z'
    ),
    (
        'a1000000-0000-4000-8000-000000000002',
        '09b98c5b-37a5-4fa3-97c7-a016d7517760',
        'ORDER_PROCESSING',
        'Order processing started.',
        '2026-11-10T09:15:00Z'
    ),
    (
        'a1000000-0000-4000-8000-000000000003',
        '09b98c5b-37a5-4fa3-97c7-a016d7517760',
        'STOCK_UNAVAILABLE',
        'Required item is temporarily out of stock.',
        '2026-11-11T10:30:00Z'
    ),
    (
        'a1000000-0000-4000-8000-000000000004',
        '09b98c5b-37a5-4fa3-97c7-a016d7517761',
        'ORDER_CREATED',
        'Synthetic order was created.',
        '2026-11-10T11:00:00Z'
    ),
    (
        'a1000000-0000-4000-8000-000000000005',
        '09b98c5b-37a5-4fa3-97c7-a016d7517761',
        'STOCK_UNAVAILABLE',
        'Required item is temporarily out of stock.',
        '2026-11-11T12:00:00Z'
    );
