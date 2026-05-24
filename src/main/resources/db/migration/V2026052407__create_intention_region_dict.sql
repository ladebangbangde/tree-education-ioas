CREATE TABLE IF NOT EXISTS intention_region (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    match_keywords VARCHAR(500) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO intention_region (code, name, display_order, match_keywords, enabled)
VALUES
    ('EUROPE', '欧洲', 10, '欧洲,法国,德国,荷兰,瑞士,爱尔兰,西班牙,意大利,北欧,丹麦,瑞典,挪威,芬兰', 1),
    ('UK', '英国', 20, '英国,英格兰,苏格兰,威尔士,伦敦,曼彻斯特,爱丁堡', 1),
    ('US', '美国', 30, '美国,美本,美硕,加州,纽约,波士顿', 1),
    ('AUSTRALIA', '澳洲', 40, '澳洲,澳大利亚,新西兰,悉尼,墨尔本,昆士兰', 1),
    ('OTHER', '其他', 99, '其他,亚洲,香港,澳门,日本,韩国,新加坡,加拿大', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    display_order = VALUES(display_order),
    match_keywords = VALUES(match_keywords),
    enabled = VALUES(enabled);
