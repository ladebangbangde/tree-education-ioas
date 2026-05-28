CREATE TABLE IF NOT EXISTS advisor_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    responsible_region VARCHAR(40) NOT NULL,
    location_region VARCHAR(40) NOT NULL,
    bio VARCHAR(1000) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_advisor_profile_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
