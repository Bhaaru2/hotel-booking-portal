INSERT INTO hotels (name, location, description, amenities, price_per_night, total_rooms)
VALUES
    (
        'The Marine Crest',
        'Mumbai',
        'A calm boutique stay overlooking the Arabian Sea, a short walk from the waterfront. Guests can start the day with a locally inspired breakfast and views of the city skyline.',
        'Wi-Fi, rooftop pool, breakfast, sea view, airport transfer',
        12500.00,
        5
    ),
    (
        'Coconut Grove House',
        'Goa',
        'A relaxed coastal retreat set among palms near the sands of Candolim. Open courtyards and a small garden make it easy to unwind between beach visits.',
        'Wi-Fi, garden, breakfast, bicycle hire, beach shuttle',
        8400.00,
        6
    ),
    (
        'Haveli Gulab Bagh',
        'Jaipur',
        'A restored heritage haveli with hand-painted details and views toward the old city. The courtyard restaurant serves regional Rajasthani dishes in the evening.',
        'Wi-Fi, courtyard restaurant, heritage decor, breakfast, guided walks',
        6900.00,
        4
    ),
    (
        'The Neem Residency',
        'Delhi',
        'A welcoming urban hotel tucked along a leafy avenue with convenient access to central Delhi. Thoughtful rooms and an all-day cafe offer a comfortable base for exploring the capital.',
        'Wi-Fi, cafe, breakfast, fitness room, airport transfer',
        9800.00,
        8
    ),
    (
        'Sandalwood Park Hotel',
        'Bengaluru',
        'A contemporary garden hotel in a quiet pocket of Bengaluru, close to the city business district. The bright lounge and shaded terrace provide inviting spaces to work or relax.',
        'Wi-Fi, garden terrace, restaurant, fitness room, meeting lounge',
        7600.00,
        7
    ),
    (
        'Backwater Fern Retreat',
        'Kochi',
        'A small waterfront hideaway inspired by the traditional homes of Kerala. Guests can enjoy peaceful backwater views and arrange a guided walk through nearby Fort Kochi.',
        'Wi-Fi, backwater view, breakfast, boat trips, bicycle hire',
        5200.00,
        3
    );

-- Generate the 120-day inventory window at migration time instead of listing dates manually.
INSERT INTO room_inventory (hotel_id, stay_date, total_rooms, booked_rooms)
WITH RECURSIVE inventory_dates (stay_date) AS (
    SELECT CURDATE()
    UNION ALL
    SELECT stay_date + INTERVAL 1 DAY
    FROM inventory_dates
    WHERE stay_date < DATE_ADD(CURDATE(), INTERVAL 119 DAY)
)
SELECT hotels.id, inventory_dates.stay_date, hotels.total_rooms, 0
FROM hotels
CROSS JOIN inventory_dates;
