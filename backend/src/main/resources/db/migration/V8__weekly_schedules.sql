CREATE TABLE photographer_schedule_weekly (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,resource_id BIGINT NOT NULL,day_of_week INT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,UNIQUE(resource_id,day_of_week),
 FOREIGN KEY(resource_id) REFERENCES employee(id),CHECK(day_of_week BETWEEN 1 AND 7));
CREATE TABLE photographer_schedule_weekly_period (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,schedule_id BIGINT NOT NULL,start_time TIME NOT NULL,end_time TIME NOT NULL,
 FOREIGN KEY(schedule_id) REFERENCES photographer_schedule_weekly(id),CHECK(start_time<end_time));
CREATE TABLE venue_schedule_weekly (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,resource_id BIGINT NOT NULL,day_of_week INT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,UNIQUE(resource_id,day_of_week),
 FOREIGN KEY(resource_id) REFERENCES venue(id),CHECK(day_of_week BETWEEN 1 AND 7));
CREATE TABLE venue_schedule_weekly_period (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,schedule_id BIGINT NOT NULL,start_time TIME NOT NULL,end_time TIME NOT NULL,
 FOREIGN KEY(schedule_id) REFERENCES venue_schedule_weekly(id),CHECK(start_time<end_time));
