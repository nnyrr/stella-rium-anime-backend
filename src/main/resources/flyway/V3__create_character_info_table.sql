CREATE TABLE `character_info` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `name` VARCHAR(255) DEFAULT NULL COMMENT '角色原名',
                                  `name_cn` VARCHAR(255) DEFAULT NULL COMMENT '角色中文名',
                                  `image` VARCHAR(1024) DEFAULT NULL COMMENT '角色图片URL',
                                  `bangumi_id` BIGINT DEFAULT NULL COMMENT 'Bangumi平台ID',
                                  `tag` VARCHAR(255) DEFAULT NULL COMMENT '主要标签/最高票Tag',
                                  `from` VARCHAR(255) DEFAULT NULL COMMENT '角色出处/来源', -- 注意：from是保留字，需使用反引号
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_bangumi_id` (`bangumi_id`) COMMENT 'BangumiID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色信息表';