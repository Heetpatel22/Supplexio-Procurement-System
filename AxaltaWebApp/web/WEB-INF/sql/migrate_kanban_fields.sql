-- Data consistency sync for bids
UPDATE bids SET user_id = supplier_id WHERE user_id IS NULL;
UPDATE bids SET bid_amount = amount WHERE bid_amount IS NULL;
