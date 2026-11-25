-- Run once before deploying the backend. Existing accounts remain FREE.
ALTER TABLE users ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'FREE';
