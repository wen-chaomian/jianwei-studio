-- Availability loads each day's intervals once, then checks all candidate starts in memory.
CREATE INDEX idx_appointment_day_window ON appointment(start_time, occupied_end_time);
CREATE INDEX idx_photographer_schedule_date ON photographer_schedule(work_date, enabled);
CREATE INDEX idx_venue_schedule_date ON venue_schedule(work_date, enabled);
