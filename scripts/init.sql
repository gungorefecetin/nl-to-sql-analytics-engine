-- QueryMind Database Initialization
-- Creates Olist dataset tables, app tables, readonly role, and indexes

-- =============================================================================
-- OLIST DATASET TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS olist_customers_dataset (
    customer_id VARCHAR(64) PRIMARY KEY,
    customer_unique_id VARCHAR(64),
    customer_zip_code_prefix VARCHAR(10),
    customer_city VARCHAR(255),
    customer_state VARCHAR(5)
);

CREATE TABLE IF NOT EXISTS olist_orders_dataset (
    order_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) REFERENCES olist_customers_dataset(customer_id),
    order_status VARCHAR(50),
    order_purchase_timestamp TIMESTAMP,
    order_approved_at TIMESTAMP,
    order_delivered_carrier_date TIMESTAMP,
    order_delivered_customer_date TIMESTAMP,
    order_estimated_delivery_date TIMESTAMP
);

CREATE TABLE IF NOT EXISTS olist_products_dataset (
    product_id VARCHAR(64) PRIMARY KEY,
    product_category_name VARCHAR(255),
    product_name_length INTEGER,
    product_description_length INTEGER,
    product_photos_qty INTEGER,
    product_weight_g INTEGER,
    product_length_cm INTEGER,
    product_height_cm INTEGER,
    product_width_cm INTEGER
);

CREATE TABLE IF NOT EXISTS olist_sellers_dataset (
    seller_id VARCHAR(64) PRIMARY KEY,
    seller_zip_code_prefix VARCHAR(10),
    seller_city VARCHAR(255),
    seller_state VARCHAR(5)
);

CREATE TABLE IF NOT EXISTS olist_order_items_dataset (
    order_id VARCHAR(64) REFERENCES olist_orders_dataset(order_id),
    order_item_id INTEGER,
    product_id VARCHAR(64) REFERENCES olist_products_dataset(product_id),
    seller_id VARCHAR(64) REFERENCES olist_sellers_dataset(seller_id),
    shipping_limit_date TIMESTAMP,
    price NUMERIC(10,2),
    freight_value NUMERIC(10,2),
    PRIMARY KEY (order_id, order_item_id)
);

CREATE TABLE IF NOT EXISTS olist_order_payments_dataset (
    order_id VARCHAR(64) REFERENCES olist_orders_dataset(order_id),
    payment_sequential INTEGER,
    payment_type VARCHAR(50),
    payment_installments INTEGER,
    payment_value NUMERIC(10,2),
    PRIMARY KEY (order_id, payment_sequential)
);

CREATE TABLE IF NOT EXISTS olist_order_reviews_dataset (
    review_id VARCHAR(64),
    order_id VARCHAR(64) REFERENCES olist_orders_dataset(order_id),
    review_score INTEGER,
    review_comment_title TEXT,
    review_comment_message TEXT,
    review_creation_date TIMESTAMP,
    review_answer_timestamp TIMESTAMP,
    PRIMARY KEY (review_id, order_id)
);

CREATE TABLE IF NOT EXISTS olist_geolocation_dataset (
    geolocation_zip_code_prefix VARCHAR(10),
    geolocation_lat DOUBLE PRECISION,
    geolocation_lng DOUBLE PRECISION,
    geolocation_city VARCHAR(255),
    geolocation_state VARCHAR(5)
);

CREATE TABLE IF NOT EXISTS product_category_name_translation (
    product_category_name VARCHAR(255) PRIMARY KEY,
    product_category_name_english VARCHAR(255)
);

-- =============================================================================
-- APPLICATION TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS query_history (
    id BIGSERIAL PRIMARY KEY,
    natural_language TEXT NOT NULL,
    generated_sql TEXT NOT NULL,
    chart_type VARCHAR(50) NOT NULL,
    result_row_count INTEGER,
    execution_time_ms INTEGER,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS schema_cache (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(255) NOT NULL UNIQUE,
    schema_json JSONB NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =============================================================================
-- INDEXES
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_query_history_created_at ON query_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status ON olist_orders_dataset(order_status);
CREATE INDEX IF NOT EXISTS idx_orders_purchase_date ON olist_orders_dataset(order_purchase_timestamp);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON olist_order_items_dataset(product_id);
CREATE INDEX IF NOT EXISTS idx_order_items_seller ON olist_order_items_dataset(seller_id);
CREATE INDEX IF NOT EXISTS idx_customers_state ON olist_customers_dataset(customer_state);
CREATE INDEX IF NOT EXISTS idx_sellers_state ON olist_sellers_dataset(seller_state);

-- =============================================================================
-- READ-ONLY ROLE
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'querymind_readonly') THEN
        EXECUTE format('CREATE ROLE querymind_readonly LOGIN PASSWORD %L', current_setting('app.readonly_password', true));
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Could not create readonly role via setting, using default password';
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'querymind_readonly') THEN
            CREATE ROLE querymind_readonly LOGIN PASSWORD 'readonly_pass_2026';
        END IF;
END
$$;

GRANT CONNECT ON DATABASE querymind TO querymind_readonly;
GRANT USAGE ON SCHEMA public TO querymind_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO querymind_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO querymind_readonly;
