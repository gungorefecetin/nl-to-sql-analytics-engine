#!/bin/bash
set -e

# QueryMind Data Seeder
# Loads Olist CSV files into PostgreSQL (idempotent)

PGUSER="querymind_admin"
PGDATABASE="querymind"

echo "========================================="
echo "  QueryMind Data Seeder"
echo "========================================="

# Check if data directory has CSV files
if [ ! -f /data/olist_orders_dataset.csv ]; then
    echo "WARNING: No CSV files found in /data/. Skipping seed."
    echo "Place Olist dataset CSVs in the data/ directory and restart."
    exit 0
fi

# Check if data is already loaded
ROW_COUNT=$(psql -U "$PGUSER" -d "$PGDATABASE" -t -c "SELECT COUNT(*) FROM olist_orders_dataset;" 2>/dev/null || echo "0")
ROW_COUNT=$(echo "$ROW_COUNT" | tr -d ' ')

if [ "$ROW_COUNT" -gt "0" ]; then
    echo "Data already loaded ($ROW_COUNT orders). Skipping seed."
    exit 0
fi

echo "Loading CSV data..."

# Load tables in dependency order (parents before children)

echo "  Loading customers..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_customers_dataset FROM '/data/olist_customers_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading orders..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_orders_dataset FROM '/data/olist_orders_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading products..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_products_dataset FROM '/data/olist_products_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading sellers..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_sellers_dataset FROM '/data/olist_sellers_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading order items..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_order_items_dataset FROM '/data/olist_order_items_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading payments..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_order_payments_dataset FROM '/data/olist_order_payments_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading reviews..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_order_reviews_dataset FROM '/data/olist_order_reviews_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading geolocation..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY olist_geolocation_dataset FROM '/data/olist_geolocation_dataset.csv' DELIMITER ',' CSV HEADER;"

echo "  Loading category translations..."
psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY product_category_name_translation FROM '/data/product_category_name_translation.csv' DELIMITER ',' CSV HEADER;"

echo ""
echo "========================================="
echo "  Seed Complete! Row counts:"
echo "========================================="
psql -U "$PGUSER" -d "$PGDATABASE" -c "
SELECT 'olist_customers_dataset' AS table_name, COUNT(*) AS rows FROM olist_customers_dataset
UNION ALL SELECT 'olist_orders_dataset', COUNT(*) FROM olist_orders_dataset
UNION ALL SELECT 'olist_products_dataset', COUNT(*) FROM olist_products_dataset
UNION ALL SELECT 'olist_sellers_dataset', COUNT(*) FROM olist_sellers_dataset
UNION ALL SELECT 'olist_order_items_dataset', COUNT(*) FROM olist_order_items_dataset
UNION ALL SELECT 'olist_order_payments_dataset', COUNT(*) FROM olist_order_payments_dataset
UNION ALL SELECT 'olist_order_reviews_dataset', COUNT(*) FROM olist_order_reviews_dataset
UNION ALL SELECT 'olist_geolocation_dataset', COUNT(*) FROM olist_geolocation_dataset
UNION ALL SELECT 'product_category_name_translation', COUNT(*) FROM product_category_name_translation
ORDER BY table_name;
"
