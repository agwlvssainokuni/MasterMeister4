CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL
);

CREATE TABLE products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(200) NOT NULL,
    category_id INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    in_stock BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATE NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

INSERT INTO categories (category_name) VALUES
    ('文具'),
    ('家電'),
    ('食品');

INSERT INTO products (product_name, category_id, unit_price, in_stock, updated_at) VALUES
    ('ボールペン', 1, 120.00, TRUE, '2026-01-10'),
    ('ノート', 1, 250.00, TRUE, '2026-02-01'),
    ('電卓', 2, 1980.00, TRUE, '2026-01-20'),
    ('デスクライト', 2, 3480.00, FALSE, '2026-03-05'),
    ('コーヒー豆', 3, 890.00, TRUE, '2026-04-12'),
    ('緑茶', 3, 450.00, TRUE, '2026-04-12');
