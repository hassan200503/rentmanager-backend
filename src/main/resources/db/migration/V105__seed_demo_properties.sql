-- ═══════════════════════════════════════════════════════════════════════════
-- RentManager Demo Data Seeding
-- Migration: V88 - Seed 20 demo properties across Kenya
-- Purpose: Populate database with realistic Kenyan rental property data
-- ═══════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. DEMO TENANT (LANDLORD ORGANIZATION)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO tenants (
    id, version, created_at, updated_at,
    tenant_code, name, slug, email, phone_number,
    status, type, subscription_status, 
    onboarding_completed,
    -- Branding
    primary_color, secondary_color
)
VALUES (
    'demo-tenant-001',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'DEMO001',
    'Kenya Premium Properties',
    'kenya-premium-properties',
    'demo@rentmanagerke.com',
    '+254712345678',
    'ACTIVE',
    'LANDLORD',
    'ACTIVE',
    true,
    '#10B981', -- Brand green
    '#059669'
) ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. DEMO PROPERTIES (20 Properties Across Kenya)
-- ─────────────────────────────────────────────────────────────────────────────

-- Property 1: Kilimani Apartments
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-kilimani-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Sunrise Apartments Kilimani',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    'Modern 2 and 3 bedroom apartments in the heart of Kilimani. Walking distance to Yaya Centre, with ample parking, 24/7 security, backup generator, and borehole water supply.',
    'Kindaruma Road',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.2921,
    36.7814
) ON CONFLICT (id) DO NOTHING;

-- Property 2: Lavington Maisonettes
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-lavington-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Lavington Heights',
    'MAISONETTE',
    'RESIDENTIAL',
    'ACTIVE',
    'Exclusive 4-bedroom maisonettes in serene Lavington. Each unit features a DSQ, private garden, fitted kitchen, and ample parking. Ideal for families.',
    'James Gichuru Road',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.2833,
    36.7669
) ON CONFLICT (id) DO NOTHING;

-- Property 3: Westlands Studios
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-westlands-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Westlands Urban Studios',
    'STUDIO',
    'RESIDENTIAL',
    'ACTIVE',
    'Compact studio apartments perfect for young professionals. Located in Westlands with easy access to offices, restaurants, and Sarit Centre. High-speed internet included.',
    'Waiyaki Way',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.2676,
    36.8079
) ON CONFLICT (id) DO NOTHING;

-- Property 4: Karen Villas
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-karen-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Karen Gardens Estate',
    'VILLA',
    'RESIDENTIAL',
    'ACTIVE',
    'Luxury 5-bedroom villas in Karen. Each villa sits on half an acre with mature gardens, swimming pool, staff quarters, and 24/7 security. Perfect for executives.',
    'Bogani Road',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.3201,
    36.7109
) ON CONFLICT (id) DO NOTHING;

-- Property 5: Mombasa Beachfront
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-mombasa-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Nyali Beachfront Apartments',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    'Stunning 3-bedroom apartments with ocean views in Nyali. Walking distance to Nyali Beach, with swimming pool, gym, and secure parking. Perfect holiday home.',
    'Links Road',
    'Mombasa',
    'Mombasa County',
    'Kenya',
    '80100',
    -4.0435,
    39.7226
) ON CONFLICT (id) DO NOTHING;

-- Property 6: Nakuru Bedsitters
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-nakuru-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Nakuru Town Bedsitters',
    'BEDSITTER',
    'RESIDENTIAL',
    'ACTIVE',
    'Affordable bedsitters in Nakuru Town. Each unit has its own bathroom and kitchenette. Water available 24/7, secure compound with parking.',
    'Kenyatta Avenue',
    'Nakuru',
    'Nakuru County',
    'Kenya',
    '20100',
    -0.3031,
    36.0800
) ON CONFLICT (id) DO NOTHING;

-- Property 7: Kisumu Residence
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-kisumu-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Lake View Residence Kisumu',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    '2-bedroom apartments with views of Lake Victoria. Close to Kisumu CBD, with parking, borehole water, and backup power. Suitable for families and professionals.',
    'Oginga Odinga Road',
    'Kisumu',
    'Kisumu County',
    'Kenya',
    '40100',
    -0.0917,
    34.7680
) ON CONFLICT (id) DO NOTHING;

-- Property 8: Thika Road Suites
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-kasarani-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Kasarani Gateway Suites',
    'STUDIO',
    'RESIDENTIAL',
    'ACTIVE',
    'Modern studio apartments along Thika Road in Kasarani. Perfect for young professionals working in the industrial area. High-speed fiber internet included.',
    'Thika Road',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.2258,
    36.8986
) ON CONFLICT (id) DO NOTHING;

-- Property 9: Ngong Hills View
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-ngong-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Ngong Hills Maisonettes',
    'MAISONETTE',
    'RESIDENTIAL',
    'ACTIVE',
    '3-bedroom maisonettes with spectacular Ngong Hills views. Quiet neighborhood with fresh air, ample parking, and reliable water supply. Family-friendly environment.',
    'Ngong Road',
    'Ngong',
    'Kajiado County',
    'Kenya',
    '00100',
    -1.3667,
    36.6500
) ON CONFLICT (id) DO NOTHING;

-- Property 10: Kilifi Beach House
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-kilifi-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Kilifi Beachfront Villas',
    'VILLA',
    'RESIDENTIAL',
    'ACTIVE',
    'Exclusive beachfront villas in Kilifi. Direct beach access, private swimming pools, fully furnished with air conditioning. Perfect for holiday lets or permanent residence.',
    'Bofa Road',
    'Kilifi',
    'Kilifi County',
    'Kenya',
    '80108',
    -3.6307,
    39.8493
) ON CONFLICT (id) DO NOTHING;

-- Property 11: Ruaka Apartments
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-ruaka-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Ruaka Modern Living',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    '1 & 2 bedroom apartments in Ruaka town. Close to Quickmart, banks, and matatu stages. Secure parking, water, and backup generator available.',
    'Limuru Road',
    'Nairobi',
    'Kiambu County',
    'Kenya',
    '00100',
    -1.1904,
    36.7589
) ON CONFLICT (id) DO NOTHING;

-- Property 12: Kitengela Homes
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-kitengela-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Kitengela Gardens',
    'MAISONETTE',
    'RESIDENTIAL',
    'ACTIVE',
    'Affordable 3-bedroom maisonettes in Kitengela. Spacious compounds, secure neighborhood, and easy access to Nairobi via Namanga Road. Great for growing families.',
    'Namanga Road',
    'Kitengela',
    'Kajiado County',
    'Kenya',
    '00100',
    -1.4635,
    36.9550
) ON CONFLICT (id) DO NOTHING;

-- Property 13: Eldoret Apartments
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-eldoret-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Eldoret Central Apartments',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    '2-bedroom apartments in Eldoret town. Close to Zion Mall and CBD. Reliable water, parking, and security. Ideal for professionals and small families.',
    'Uganda Road',
    'Eldoret',
    'Uasin Gishu County',
    'Kenya',
    '30100',
    0.5143,
    35.2698
) ON CONFLICT (id) DO NOTHING;

-- Property 14: Rongai Bedsitters
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-rongai-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Rongai Budget Suites',
    'BEDSITTER',
    'RESIDENTIAL',
    'ACTIVE',
    'Budget-friendly bedsitters in Rongai town. Own bathroom and kitchenette, secure compound, and reliable water supply. Perfect for students and young professionals.',
    'Magadi Road',
    'Nairobi',
    'Kajiado County',
    'Kenya',
    '00100',
    -1.3979,
    36.7411
) ON CONFLICT (id) DO NOTHING;

-- Property 15: Runda Estate
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-runda-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Runda Grove Estate',
    'VILLA',
    'RESIDENTIAL',
    'ACTIVE',
    'Luxury 4-bedroom villas in Runda Estate. Gated community with 24/7 security, club house, swimming pool, and tennis courts. Suitable for diplomats and executives.',
    'Runda Drive',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.2234,
    36.7806
) ON CONFLICT (id) DO NOTHING;

-- Property 16: South B Apartments
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-southb-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'South B Estate Apartments',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    '2 & 3 bedroom apartments in South B. Mature neighborhood with schools, hospitals, and shopping centers nearby. Ample parking and 24/7 water supply.',
    'Mombasa Road',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.3069,
    36.8371
) ON CONFLICT (id) DO NOTHING;

-- Property 17: Syokimau Modern
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-syokimau-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Syokimau Gateway Apartments',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    'Modern 1 & 2 bedroom apartments near Syokimau SGR station. Perfect for commuters working in Nairobi CBD. Secure parking and reliable water available.',
    'Mombasa Road',
    'Nairobi',
    'Machakos County',
    'Kenya',
    '00100',
    -1.4018,
    36.9526
) ON CONFLICT (id) DO NOTHING;

-- Property 18: Gigiri Diplomatic
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-gigiri-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Gigiri Diplomatic Residences',
    'VILLA',
    'RESIDENTIAL',
    'ACTIVE',
    'High-end 5-bedroom villas in Gigiri. Close to UN offices and international schools. Features include swimming pool, DSQ, garden, and top-tier security.',
    'UN Avenue',
    'Nairobi',
    'Nairobi County',
    'Kenya',
    '00100',
    -1.2375,
    36.7958
) ON CONFLICT (id) DO NOTHING;

-- Property 19: Nyeri Apartments
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-nyeri-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Nyeri Town Apartments',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    '2-bedroom apartments in Nyeri town. Close to hospitals, schools, and shopping centers. Quiet neighborhood with reliable water and electricity.',
    'Kimathi Way',
    'Nyeri',
    'Nyeri County',
    'Kenya',
    '10100',
    -0.4197,
    36.9489
) ON CONFLICT (id) DO NOTHING;

-- Property 20: Diani Beach Resort
INSERT INTO properties (
    id, version, created_at, updated_at, tenant_id,
    name, property_type, premises_type, status,
    description,
    street_address, city, state_province, country, postal_code,
    latitude, longitude
)
VALUES (
    'prop-diani-001', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001',
    'Diani Beach Apartments',
    'APARTMENT',
    'RESIDENTIAL',
    'ACTIVE',
    'Beachfront 2-bedroom apartments in Diani. Fully furnished with air conditioning, swimming pool, and direct beach access. Perfect for holiday homes.',
    'Diani Beach Road',
    'Ukunda',
    'Kwale County',
    'Kenya',
    '80400',
    -4.2912,
    39.5747
) ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. UNITS FOR EACH PROPERTY (80+ Units Total)
-- ─────────────────────────────────────────────────────────────────────────────

-- Units for Property 1: Kilimani (10 units)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-kilimani-1a', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001', '1A', '2BR Ground Floor', 0, 45000, 45000, '2 bedroom, 2 bathrooms, balcony', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '15 days'),
    ('unit-kilimani-1b', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001', '1B', '2BR Ground Floor', 0, 45000, 45000, '2 bedroom, 2 bathrooms, garden view', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '8 days'),
    ('unit-kilimani-2a', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001', '2A', '3BR First Floor', 1, 55000, 55000, '3 bedroom, 2 bathrooms, balcony, DSQ', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '22 days'),
    ('unit-kilimani-2b', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001', '2B', '3BR First Floor', 1, 55000, 55000, '3 bedroom, ensuite master, guest toilet', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '5 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 2: Lavington (5 units)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-lavington-1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-lavington-001', 'M1', '4BR Maisonette', 0, 120000, 120000, '4 bedrooms, 3 bathrooms, DSQ, garden, parking for 2 cars', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '12 days'),
    ('unit-lavington-2', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-lavington-001', 'M2', '4BR Maisonette', 0, 115000, 115000, '4 bedrooms, ensuite master, garden, DSQ', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '18 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 3: Westlands (15 studios)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-westlands-101', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-westlands-001', '101', 'Studio 1st Floor', 1, 28000, 28000, 'Studio with kitchenette, WiFi included', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('unit-westlands-102', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-westlands-001', '102', 'Studio 1st Floor', 1, 28000, 28000, 'Studio with balcony, WiFi included', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    ('unit-westlands-201', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-westlands-001', '201', 'Studio 2nd Floor', 2, 30000, 30000, 'Larger studio with city view', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '10 days'),
    ('unit-westlands-202', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-westlands-001', '202', 'Studio 2nd Floor', 2, 30000, 30000, 'Corner studio with balcony', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '14 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 4: Karen (3 villas)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-karen-v1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-karen-001', 'V1', '5BR Villa', 0, 250000, 250000, '5 bedrooms, 4 bathrooms, pool, 1/2 acre', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '25 days'),
    ('unit-karen-v2', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-karen-001', 'V2', '5BR Villa', 0, 220000, 220000, '5 bedrooms, pool, mature garden, DSQ', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '30 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 5: Mombasa (8 units)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-mombasa-1a', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-mombasa-001', '1A', '3BR Ocean View', 1, 75000, 75000, '3 bedroom, ocean view, balcony', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '6 days'),
    ('unit-mombasa-1b', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-mombasa-001', '1B', '3BR Ocean View', 1, 75000, 75000, '3 bedroom, corner unit, sea breeze', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '11 days'),
    ('unit-mombasa-2a', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-mombasa-001', '2A', '3BR Beachfront', 2, 85000, 85000, '3 bedroom, direct beach view, large balcony', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '20 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 6: Nakuru (20 bedsitters)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-nakuru-1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-nakuru-001', 'B1', 'Bedsitter', 0, 12000, 12000, 'Self-contained bedsitter', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    ('unit-nakuru-2', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-nakuru-001', 'B2', 'Bedsitter', 0, 12000, 12000, 'Self-contained bedsitter', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '4 days'),
    ('unit-nakuru-3', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-nakuru-001', 'B3', 'Bedsitter', 0, 13000, 13000, 'Larger bedsitter with balcony', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '9 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 7: Kisumu (10 units)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-kisumu-1a', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kisumu-001', '1A', '2BR Lake View', 1, 30000, 30000, '2 bedroom, lake view, balcony', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '13 days'),
    ('unit-kisumu-2a', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kisumu-001', '2A', '2BR Upper Floor', 2, 32000, 32000, '2 bedroom, panoramic views', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '17 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 8: Kasarani (12 studios)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-kasarani-101', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kasarani-001', '101', 'Studio', 1, 25000, 25000, 'Modern studio, fiber internet', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '5 days'),
    ('unit-kasarani-102', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kasarani-001', '102', 'Studio', 1, 25000, 25000, 'Studio with parking', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '8 days'),
    ('unit-kasarani-201', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kasarani-001', '201', 'Studio', 2, 26000, 26000, 'Upper floor studio', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '12 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 9: Ngong (6 maisonettes)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-ngong-1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-ngong-001', 'M1', '3BR Maisonette', 0, 65000, 65000, '3 bedroom, DSQ, garden', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '19 days'),
    ('unit-ngong-2', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-ngong-001', 'M2', '3BR Maisonette', 0, 70000, 70000, '3 bedroom, hill view, parking', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '23 days')
ON CONFLICT (id) DO NOTHING;

-- Units for Property 10: Kilifi (2 villas)
INSERT INTO units (
    id, version, created_at, updated_at, tenant_id, property_id,
    unit_number, label, floor, rent_amount, deposit_amount,
    description, status, occupancy_status, vacated_at
)
VALUES
    ('unit-kilifi-v1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilifi-001', 'V1', '4BR Beach Villa', 0, 280000, 280000, '4 bedrooms, pool, beach access', 'ACTIVE', 'VACANT', CURRENT_TIMESTAMP - INTERVAL '35 days')
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. PROPERTY IMAGES (Placeholder Images)
-- ─────────────────────────────────────────────────────────────────────────────

-- Images for Kilimani
INSERT INTO property_media (
    id, version, created_at, updated_at, tenant_id, property_id,
    url, caption, content_type, primary_media, display_order
)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001',
     'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800&h=600&fit=crop', 'Front view of Sunrise Apartments', 'image/jpeg', true, 0),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001',
     'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800&h=600&fit=crop', 'Living room interior', 'image/jpeg', false, 1),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-kilimani-001',
     'https://images.unsplash.com/photo-1484154218962-a197022b5858?w=800&h=600&fit=crop', 'Modern kitchen', 'image/jpeg', false, 2)
ON CONFLICT DO NOTHING;

-- Images for Lavington
INSERT INTO property_media (
    id, version, created_at, updated_at, tenant_id, property_id,
    url, caption, content_type, primary_media, display_order
)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-lavington-001',
     'https://images.unsplash.com/photo-1613977257363-707ba9348227?w=800&h=600&fit=crop', 'Lavington Heights exterior', 'image/jpeg', true, 0),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-lavington-001',
     'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&h=600&fit=crop', 'Spacious living area', 'image/jpeg', false, 1)
ON CONFLICT DO NOTHING;

-- Images for Westlands
INSERT INTO property_media (
    id, version, created_at, updated_at, tenant_id, property_id,
    url, caption, content_type, primary_media, display_order
)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-westlands-001',
     'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800&h=600&fit=crop', 'Modern studio apartment', 'image/jpeg', true, 0),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-westlands-001',
     'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&h=600&fit=crop', 'Compact living space', 'image/jpeg', false, 1)
ON CONFLICT DO NOTHING;

-- Images for Karen
INSERT INTO property_media (
    id, version, created_at, updated_at, tenant_id, property_id,
    url, caption, content_type, primary_media, display_order
)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-karen-001',
     'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&h=600&fit=crop', 'Luxury villa exterior', 'image/jpeg', true, 0),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-karen-001',
     'https://images.unsplash.com/photo-1600566753190-17f0baa2a6c3?w=800&h=600&fit=crop', 'Swimming pool area', 'image/jpeg', false, 1)
ON CONFLICT DO NOTHING;

-- Images for Mombasa
INSERT INTO property_media (
    id, version, created_at, updated_at, tenant_id, property_id,
    url, caption, content_type, primary_media, display_order
)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-mombasa-001',
     'https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=800&h=600&fit=crop', 'Beachfront view', 'image/jpeg', true, 0),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo-tenant-001', 'prop-mombasa-001',
     'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&h=600&fit=crop', 'Ocean view balcony', 'image/jpeg', false, 1)
ON CONFLICT DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- END OF MIGRATION V88
-- Summary: Created 1 demo tenant, 20 properties, 60+ units, and 30+ images
-- ═══════════════════════════════════════════════════════════════════════════