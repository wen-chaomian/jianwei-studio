-- Persistent lock anchors, not reservations. Authoritative occupancy remains in appointment.
CREATE TABLE booking_resource_slot_lock (
 resource_kind VARCHAR(16) NOT NULL,
 resource_id BIGINT NOT NULL,
 slot_start DATETIME NOT NULL,
 PRIMARY KEY(resource_kind,resource_id,slot_start)
) ENGINE=InnoDB;
