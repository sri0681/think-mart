INSERT INTO products (id, name, category, price, rating, tag, description, image) VALUES
('laptop', 'Everyday Performance Laptop', 'Tech', 50000, 4.8, 'Bestseller', 'A powerful, reliable laptop for work, study, and play.', 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=85'),
('headphones', 'Studio Wireless Headphones', 'Tech', 8500, 4.6, 'Popular', 'Comfortable wireless listening with clear, balanced sound.', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=85'),
('watch', 'Classic Everyday Watch', 'Accessories', 4200, 4.5, 'Timeless', 'A durable everyday watch with a clean, minimal design.', 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=85'),
('phone', 'Nova Smartphone', 'Tech', 32000, 4.7, 'New', 'A bright display, capable camera, and all-day battery.', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&q=85'),
('backpack', 'City Explorer Backpack', 'Accessories', 2900, 4.4, 'Everyday', 'Thoughtful pockets and a comfortable fit for daily journeys.', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=85'),
('camera', 'Instant Moments Camera', 'Tech', 12900, 4.5, 'Creative', 'Capture and print life''s little moments wherever you go.', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=85'),
('lamp', 'Soft Glow Table Lamp', 'Home', 3600, 4.3, 'Home pick', 'Warm, calming light for a desk, bedside, or reading nook.', 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=85'),
('speaker', 'Portable Bluetooth Speaker', 'Tech', 5400, 4.6, 'Portable', 'Room-filling sound in a compact speaker made to travel.', 'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=900&q=85')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    category = VALUES(category),
    price = VALUES(price),
    rating = VALUES(rating),
    tag = VALUES(tag),
    description = VALUES(description),
    image = VALUES(image);
