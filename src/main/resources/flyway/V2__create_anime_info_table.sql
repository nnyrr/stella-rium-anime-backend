CREATE TABLE IF NOT EXISTS `anime_info` (
                                            `bangumi_id` BIGINT NOT NULL COMMENT 'Bangumi ID (主键)',
                                            `name` VARCHAR(255) NOT NULL COMMENT '原名',
                                            `name_cn` VARCHAR(255) DEFAULT NULL COMMENT '中文名',
                                            `rating` DOUBLE DEFAULT 0.0 COMMENT '评分',
                                            `image` VARCHAR(1024) DEFAULT NULL COMMENT '图片链接',
                                            `tag` VARCHAR(255) DEFAULT NULL COMMENT '最高票标签',
                                            `year` VARCHAR(20) DEFAULT NULL COMMENT '年份 (String类型)',
                                            `summary` TEXT COMMENT '简介',
                                            PRIMARY KEY (`bangumi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='番剧详细信息表';