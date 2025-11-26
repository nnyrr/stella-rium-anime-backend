CREATE TABLE `user` (
                        `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '本站唯一id',
                        `username` varchar(64) NOT NULL COMMENT '用户名(登录用)',
                        `nickname` varchar(64) DEFAULT NULL COMMENT '昵称(显示用)',
                        `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
                        `password` varchar(128) DEFAULT NULL COMMENT '加密密码',
                        `image` varchar(512) DEFAULT NULL COMMENT '头像URL',
                        `bangumi_id` varchar(64) DEFAULT NULL COMMENT '第三方Bangumi唯一id',
                        `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        PRIMARY KEY (`user_id`),
                        UNIQUE KEY `uk_username` (`username`),
                        UNIQUE KEY `uk_email` (`email`),
                        UNIQUE KEY `uk_bangumi_id` (`bangumi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';