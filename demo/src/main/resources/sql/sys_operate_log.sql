-- =============================================
-- 6. 操作日志表
-- =============================================
DROP TABLE IF EXISTS `sys_operate_log`;
CREATE TABLE `sys_operate_log` (
  `id`            BIGINT       NOT NULL COMMENT '日志ID（雪花算法）',
  `title`         VARCHAR(64)  NOT NULL COMMENT '模块标题，如"用户管理"',
  `business_type` TINYINT      NOT NULL COMMENT '操作类型：0=其他 1=新增 2=修改 3=删除',
  `method`        VARCHAR(200) NOT NULL COMMENT '调用的Java方法全路径',
  `request_method` VARCHAR(10) NOT NULL COMMENT 'HTTP方法：GET/POST/PUT/DELETE',
  `oper_url`      VARCHAR(200) NOT NULL COMMENT '请求URL',
  `oper_name`     VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名（从SecurityContext取）',
  `oper_param`    TEXT         COMMENT '请求参数（JSON）',
  `oper_result`   TEXT         COMMENT '返回结果（JSON）',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '操作状态：0=正常 1=异常',
  `error_msg`     TEXT         COMMENT '异常信息（正常时为空）',
  `oper_ip`       VARCHAR(128) DEFAULT NULL COMMENT '操作者IP',
  `oper_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time`     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  PRIMARY KEY (`id`),
  KEY `idx_oper_name` (`oper_name`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_business_type` (`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
