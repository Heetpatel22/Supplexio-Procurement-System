-- Update existing rows to set needs_auction based on kanban_status
UPDATE inventory SET needs_auction = (kanban_status = 'Low') WHERE needs_auction IS NULL;
