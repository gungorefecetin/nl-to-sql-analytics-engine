#!/bin/bash
# ============================================================
# QueryMind — CSV Data Loader
# Loads Olist dataset CSVs into PostgreSQL tables.
# Idempotent: skips tables that already contain data.
# Runs as Docker entrypoint script (02-seed.sh)
# ============================================================

set -e

PGUSER="${POSTGRES_USER:-querymind_admin}"
PGDATABASE="${POSTGRES_DB:-querymind}"

DATA_DIR="/data"

# Check if CSV files exist
if [ ! -d "$DATA_DIR" ] || [ -z "$(ls -A $DATA_DIR/*.csv 2>/dev/null)" ]; then
    echo "⚠ No CSV files found in $DATA_DIR. Skipping seed."
    echo "  Download the Olist dataset from Kaggle and place CSVs in ./data/"
    exit 0
fi

# Helper: load a CSV into a table if the table is empty
load_csv() {
    local table="$1"
    local file="$2"

    if [ ! -f "$DATA_DIR/$file" ]; then
        echo "  SKIP $table — file $file not found"
        return
    fi

    count=$(psql -U "$PGUSER" -d "$PGDATABASE" -tAc "SELECT COUNT(*) FROM $table;" 2>/dev/null || echo "0")

    if [ "$count" -gt "0" ]; then
        echo "  SKIP $table — already has $count rows"
    else
        echo "  LOAD $table ← $file"
        psql -U "$PGUSER" -d "$PGDATABASE" -c "\COPY $table FROM '$DATA_DIR/$file' WITH (FORMAT csv, HEADER true, DELIMITER ',');"
        new_count=$(psql -U "$PGUSER" -d "$PGDATABASE" -tAc "SELECT COUNT(*) FROM $table;")
        echo "       → $new_count rows loaded"
    fi
}

echo ""
echo "=== QueryMind Data Seeder ==="
echo ""

# Load order matters: parent tables first (FK constraints)
load_csv "olist_customers_dataset"              "olist_customers_dataset.csv"
load_csv "olist_sellers_dataset"                "olist_sellers_dataset.csv"
load_csv "olist_products_dataset"               "olist_products_dataset.csv"
load_csv "olist_orders_dataset"                 "olist_orders_dataset.csv"
load_csv "olist_order_items_dataset"            "olist_order_items_dataset.csv"
load_csv "olist_order_payments_dataset"         "olist_order_payments_dataset.csv"
load_csv "olist_order_reviews_dataset"          "olist_order_reviews_dataset.csv"
load_csv "olist_geolocation_dataset"            "olist_geolocation_dataset.csv"
load_csv "product_category_name_translation"    "product_category_name_translation.csv"

echo ""
echo "=== Seeding complete ==="
echo ""
