-- Nacos 3.0.3 DB 模式建表脚本（达梦 DM8）。
-- 对应 Nacos 发行包 conf/mysql-schema.sql 的达梦语法改写：反引号去掉、AUTO_INCREMENT 改 IDENTITY、
-- longtext/text 改 CLOB、UNIQUE KEY 改表级约束、KEY 改独立索引、去掉 ENGINE/CHARSET/COMMENT 子句。
-- 可重复执行：先 DROP 再创建，等同重置 Nacos 配置库（配置内容会被清空，需要重新发布 backend/nacos/config）。
-- 执行位置：与业务表同库（SYSDBA）。与 env/sql/dm8/schema.sql 分开维护：本脚本只服务 Nacos 配置库，
-- 不要放进 env/sql/dm8/ 目录，否则会被 init-dm8-sql.sh 的 *.sql 通配一并执行并重置配置数据。

/******************************************/
/* config_info                            */
/******************************************/
DROP TABLE IF EXISTS config_info;
CREATE TABLE config_info (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    data_id            VARCHAR(255) NOT NULL,
    group_id           VARCHAR(128) DEFAULT NULL,
    content            CLOB NOT NULL,
    md5                VARCHAR(32) DEFAULT NULL,
    gmt_create         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- MySQL 原文为 longtext；DM8 不允许 CLOB 与字符串参数做 <> 比较（内部命名空间迁移会报 Data type mismatch），故用 VARCHAR
    src_user           VARCHAR(1024),
    src_ip             VARCHAR(50) DEFAULT NULL,
    app_name           VARCHAR(128) DEFAULT NULL,
    tenant_id          VARCHAR(128) DEFAULT '',
    c_desc             VARCHAR(256) DEFAULT NULL,
    c_use              VARCHAR(64) DEFAULT NULL,
    effect             VARCHAR(64) DEFAULT NULL,
    type               VARCHAR(64) DEFAULT NULL,
    c_schema           CLOB,
    encrypted_data_key VARCHAR(1024) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    CONSTRAINT uk_configinfo_datagrouptenant UNIQUE (data_id, group_id, tenant_id)
);

/******************************************/
/* config_info_gray                       */
/******************************************/
DROP TABLE IF EXISTS config_info_gray;
CREATE TABLE config_info_gray (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    data_id            VARCHAR(255) NOT NULL,
    group_id           VARCHAR(128) NOT NULL,
    content            CLOB NOT NULL,
    md5                VARCHAR(32) DEFAULT NULL,
    -- 同 config_info.src_user：参与 <> 比较，不能用 CLOB
    src_user           VARCHAR(1024),
    src_ip             VARCHAR(100) DEFAULT NULL,
    gmt_create         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    app_name           VARCHAR(128) DEFAULT NULL,
    tenant_id          VARCHAR(128) DEFAULT '',
    gray_name          VARCHAR(128) NOT NULL,
    gray_rule          CLOB NOT NULL,
    encrypted_data_key VARCHAR(256) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    CONSTRAINT uk_configinfogray_datagrouptenantgray UNIQUE (data_id, group_id, tenant_id, gray_name)
);
CREATE INDEX idx_dataid_gmt_modified ON config_info_gray (data_id, gmt_modified);
CREATE INDEX idx_gmt_modified ON config_info_gray (gmt_modified);

/******************************************/
/* config_tags_relation                   */
/******************************************/
DROP TABLE IF EXISTS config_tags_relation;
CREATE TABLE config_tags_relation (
    id        BIGINT NOT NULL,
    tag_name  VARCHAR(128) NOT NULL,
    tag_type  VARCHAR(64) DEFAULT NULL,
    data_id   VARCHAR(255) NOT NULL,
    group_id  VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) DEFAULT '',
    nid       BIGINT IDENTITY(1,1) NOT NULL,
    PRIMARY KEY (nid),
    CONSTRAINT uk_configtagrelation_configidtag UNIQUE (id, tag_name, tag_type)
);
CREATE INDEX idx_tenant_id ON config_tags_relation (tenant_id);

/******************************************/
/* group_capacity                         */
/******************************************/
DROP TABLE IF EXISTS group_capacity;
CREATE TABLE group_capacity (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    group_id        VARCHAR(128) NOT NULL DEFAULT '',
    quota           INT NOT NULL DEFAULT 0,
    usage           INT NOT NULL DEFAULT 0,
    max_size        INT NOT NULL DEFAULT 0,
    max_aggr_count  INT NOT NULL DEFAULT 0,
    max_aggr_size   INT NOT NULL DEFAULT 0,
    max_history_count INT NOT NULL DEFAULT 0,
    gmt_create      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_id UNIQUE (group_id)
);

/******************************************/
/* his_config_info                        */
/******************************************/
DROP TABLE IF EXISTS his_config_info;
CREATE TABLE his_config_info (
    id                 BIGINT NOT NULL,
    nid                BIGINT IDENTITY(1,1) NOT NULL,
    data_id            VARCHAR(255) NOT NULL,
    group_id           VARCHAR(128) NOT NULL,
    app_name           VARCHAR(128) DEFAULT NULL,
    content            CLOB NOT NULL,
    md5                VARCHAR(32) DEFAULT NULL,
    gmt_create         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    src_user           VARCHAR(1024),
    src_ip             VARCHAR(50) DEFAULT NULL,
    op_type            CHAR(10) DEFAULT NULL,
    tenant_id          VARCHAR(128) DEFAULT '',
    encrypted_data_key VARCHAR(1024) NOT NULL DEFAULT '',
    publish_type       VARCHAR(50) DEFAULT 'formal',
    gray_name          VARCHAR(50) DEFAULT NULL,
    ext_info           CLOB,
    PRIMARY KEY (nid)
);
CREATE INDEX idx_gmt_create_his ON his_config_info (gmt_create);
CREATE INDEX idx_gmt_modified_his ON his_config_info (gmt_modified);
CREATE INDEX idx_did ON his_config_info (data_id);

/******************************************/
/* tenant_capacity                        */
/******************************************/
DROP TABLE IF EXISTS tenant_capacity;
CREATE TABLE tenant_capacity (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    tenant_id       VARCHAR(128) NOT NULL DEFAULT '',
    quota           INT NOT NULL DEFAULT 0,
    usage           INT NOT NULL DEFAULT 0,
    max_size        INT NOT NULL DEFAULT 0,
    max_aggr_count  INT NOT NULL DEFAULT 0,
    max_aggr_size   INT NOT NULL DEFAULT 0,
    max_history_count INT NOT NULL DEFAULT 0,
    gmt_create      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_id UNIQUE (tenant_id)
);

/******************************************/
/* tenant_info                            */
/******************************************/
DROP TABLE IF EXISTS tenant_info;
CREATE TABLE tenant_info (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    kp            VARCHAR(128) NOT NULL,
    tenant_id     VARCHAR(128) DEFAULT '',
    tenant_name   VARCHAR(128) DEFAULT '',
    tenant_desc   VARCHAR(256) DEFAULT NULL,
    create_source VARCHAR(32) DEFAULT NULL,
    gmt_create    BIGINT NOT NULL,
    gmt_modified  BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_info_kptenantid UNIQUE (kp, tenant_id)
);
CREATE INDEX idx_tenant_id_info ON tenant_info (tenant_id);

/******************************************/
/* 认证：users / roles / permissions      */
/******************************************/
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    username VARCHAR(50) NOT NULL,
    password VARCHAR(500) NOT NULL,
    enabled  TINYINT NOT NULL, -- DM8 无 BOOLEAN 列类型，用 TINYINT 存 0/1
    PRIMARY KEY (username)
);

DROP TABLE IF EXISTS roles;
CREATE TABLE roles (
    username VARCHAR(50) NOT NULL,
    role     VARCHAR(50) NOT NULL,
    CONSTRAINT idx_user_role UNIQUE (username, role)
);

DROP TABLE IF EXISTS permissions;
CREATE TABLE permissions (
    role     VARCHAR(50) NOT NULL,
    resource VARCHAR(128) NOT NULL,
    action   VARCHAR(8) NOT NULL,
    CONSTRAINT uk_role_permission UNIQUE (role, resource, action)
);
