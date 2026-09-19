-- V3: real product images (emoji remains the fallback when absent)
ALTER TABLE auctions ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
