-- Supplexio Seed Data
USE axalta;

-- Default Admin User (username: admin, password: admin123)
INSERT IGNORE INTO users (id, username, password_hash, role, status, email) VALUES
(1, 'admin', 'admin123', 'ADMIN', 'ACTIVE', 'admin@example.com'),
(2, 'buyer1', 'buyer123', 'USER', 'ACTIVE', 'buyer1@example.com'),
(3, 'supplier1', 'supplier123', 'SUPPLIER', 'ACTIVE', 'supplier1@example.com');

-- Sample Inventory Items
INSERT IGNORE INTO inventory (id, item_name, quantity, min_threshold, max_threshold, kanban_status, needs_auction, auction_started) VALUES
(1, 'Steel Rods', 50, 20, 100, 'Medium', FALSE, FALSE),
(2, 'Copper Sheets', 30, 10, 80, 'High', FALSE, FALSE),
(3, 'Aluminum Plates', 80, 40, 120, 'Medium', FALSE, FALSE),
(4, 'Plastic Granules', 120, 60, 200, 'Medium', FALSE, FALSE),
(5, 'Paint Buckets', 15, 25, 100, 'Low', TRUE, FALSE);

-- Sample Products
INSERT IGNORE INTO products (id, name, description, category, base_price, unit, stock_quantity, inventory_id) VALUES
(1, 'Steel Rods', 'High tensile industrial steel rods', 'Raw Materials', 150.00, 'pcs', 50, 1),
(2, 'Copper Sheets', 'High-conductivity grade electrolytic copper sheets', 'Raw Materials', 220.00, 'pcs', 30, 2),
(3, 'Aluminum Plates', 'Aerospace alloy 6061-T6 aluminum plates', 'Raw Materials', 180.00, 'pcs', 80, 3),
(4, 'Plastic Granules', 'Polypropylene injection molding granules', 'Polymers', 45.00, 'kg', 120, 4),
(5, 'Industrial Primer - White', 'Anti-corrosion white primer for metal surfaces', 'Coatings', 65.00, 'gallon', 15, 5),
(6, 'Acrylic Paint - Gloss Blue', 'Weather-resistant acrylic finish paint', 'Coatings', 75.00, 'gallon', 25, NULL),
(7, 'Epoxy Resin Clear', 'Two-part industrial coating epoxy resin', 'Resins', 95.00, 'liter', 40, NULL);

-- Sample Suppliers
INSERT IGNORE INTO suppliers (id, name, email, phone, address, city, state, country, postal_code, company_name, contact_person, user_id, password, status) VALUES
(1, 'Acme Industrial Supplies', 'supplier1@example.com', '+1-555-0101', '100 Supply Ave', 'Chicago', 'IL', 'USA', '60601', 'Acme Industrial LLC', 'John Doe', 3, 'supplier123', 'ACTIVE'),
(2, 'Apex Metals & Alloys', 'supplier2@example.com', '+1-555-0102', '200 Forge Rd', 'Pittsburgh', 'PA', 'USA', '15201', 'Apex Metals Inc', 'Jane Smith', NULL, 'password123', 'ACTIVE'),
(3, 'Global Coating Solutions', 'supplier3@example.com', '+1-555-0103', '300 Polymer Way', 'Houston', 'TX', 'USA', '77001', 'Global Coatings Corp', 'Bob Wilson', NULL, 'password123', 'ACTIVE');

-- Sample Active Reverse Auction
INSERT IGNORE INTO reverse_auctions (id, product_id, start_price, current_price, required_quantity, start_time, end_time, status) VALUES
(1, 1, 15000.00, 12500.00, 100, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 24 HOUR, 'ACTIVE'),
(2, 5, 6500.00, 5800.00, 50, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 12 HOUR, 'ACTIVE');

-- Sample Auction Invitations
INSERT IGNORE INTO auction_invitations (auction_id, supplier_id, status) VALUES
(1, 1, 'ACCEPTED'),
(1, 2, 'ACCEPTED'),
(2, 1, 'ACCEPTED'),
(2, 3, 'ACCEPTED');

-- Sample Bids
INSERT IGNORE INTO bids (id, auction_id, supplier_id, user_id, amount, bid_amount, bid_time) VALUES
(1, 1, 1, 1, 14500.00, 14500.00, NOW() - INTERVAL 50 MINUTE),
(2, 1, 2, 2, 13800.00, 13800.00, NOW() - INTERVAL 40 MINUTE),
(3, 1, 1, 1, 13000.00, 13000.00, NOW() - INTERVAL 25 MINUTE),
(4, 1, 2, 2, 12500.00, 12500.00, NOW() - INTERVAL 10 MINUTE),
(5, 2, 3, 3, 6200.00, 6200.00, NOW() - INTERVAL 90 MINUTE),
(6, 2, 1, 1, 5800.00, 5800.00, NOW() - INTERVAL 30 MINUTE);

-- Sample Bid Chart Data
INSERT IGNORE INTO bid_chart_data (auction_id, bid_time, bid_amount, supplier_id, supplier_name, bid_id) VALUES
(1, NOW() - INTERVAL 50 MINUTE, 14500.00, 1, 'Acme Industrial Supplies', 1),
(1, NOW() - INTERVAL 40 MINUTE, 13800.00, 2, 'Apex Metals & Alloys', 2),
(1, NOW() - INTERVAL 25 MINUTE, 13000.00, 1, 'Acme Industrial Supplies', 3),
(1, NOW() - INTERVAL 10 MINUTE, 12500.00, 2, 'Apex Metals & Alloys', 4);

-- Sample Todos
INSERT IGNORE INTO todos (id, title, description, due_date, priority, status) VALUES
(1, 'Review Paint Buckets stock', 'Stock is below 25 min threshold. Initiate procurement.', CURDATE() + INTERVAL 1 DAY, 'High', 'Pending'),
(2, 'Confirm PO #1001 delivery', 'Contact Acme Industrial for shipment tracking.', CURDATE() + INTERVAL 2 DAY, 'Medium', 'Pending'),
(3, 'Verify Q3 Supplier compliance', 'Audit all active suppliers for ISO certification.', CURDATE() + INTERVAL 7 DAY, 'Low', 'Pending');

