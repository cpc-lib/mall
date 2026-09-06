-- =====================================================================
-- 支付渠道商户配置重构升级脚本（V6 → 渠道商户配置）
-- 适用：已按旧版 payment_demo.sql 初始化的存量 DM8 库。
-- 新库直接执行 payment_demo.sql，无需本脚本。
-- 内容：
--   1. t_payment_channel 新增 10 个商户列
--   2. 回填微信/支付宝商户种子数据（含 apiclient_key.pem PEM 内容入库）
--   3. t_payment_app 删除 app_config 列
-- 幂等性：DM8 不支持 IF EXISTS 增列，重复执行会在增列处报错，属预期。
-- =====================================================================

-- 1. t_payment_channel 新增商户列
ALTER TABLE t_payment_channel ADD appid VARCHAR(64);
ALTER TABLE t_payment_channel ADD mch_id VARCHAR(32);
ALTER TABLE t_payment_channel ADD mch_serial_no VARCHAR(64);
ALTER TABLE t_payment_channel ADD private_key CLOB;
ALTER TABLE t_payment_channel ADD api_v3_key VARCHAR(128);
ALTER TABLE t_payment_channel ADD partner_key VARCHAR(128);
ALTER TABLE t_payment_channel ADD alipay_app_id VARCHAR(64);
ALTER TABLE t_payment_channel ADD seller_id VARCHAR(64);
ALTER TABLE t_payment_channel ADD merchant_private_key CLOB;
ALTER TABLE t_payment_channel ADD alipay_public_key CLOB;

COMMENT ON COLUMN t_payment_channel.appid IS '微信appid（公众号/小程序/APP），WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.mch_id IS '微信商户号，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.mch_serial_no IS '微信商户API证书序列号，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.private_key IS '微信商户私钥PEM内容（\n转义或真实换行，代码侧归一化），替代apiclient_key.pem文件';
COMMENT ON COLUMN t_payment_channel.api_v3_key IS '微信APIv3密钥，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.partner_key IS '微信APIv2密钥，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.alipay_app_id IS '支付宝应用ID，ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.seller_id IS '支付宝卖家PID，ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.merchant_private_key IS '支付宝应用私钥（base64），ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.alipay_public_key IS '支付宝公钥（base64），ALIPAY渠道使用';

-- 2. 回填商户种子数据（与 payment_demo.sql 保持一致）
-- 微信：原 t_payment_app.app_config JSON 中的商户参数 + apiclient_key.pem 内容（\n 转义单行存储）
UPDATE t_payment_channel SET
  appid = 'wx74862e0dfcf69954',
  mch_id = '1558950191',
  mch_serial_no = '34345964330B66427E0D3D28826C4993C77E631F',
  private_key = '-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDnSAKI8sea8p+d\nOBVPWlZmxqJfPbdhzZxdI5Kx1j5SJNZwXWtr43/giw38pwzSlBI+bubBcYlkFTI0\nguigMZO/yueb1mZChaY/JG1vsT02Ubj0xkVvBwKNbYS48NEpZhK61Mia09R4n1iH\n1vip9kt8J6Zrx+xIqwmuCNWigyivGrvY9AdevCNlNSVdHVOZUJiJ6UGtvVmgZb0u\nRTwBzfkjnwTgEcsrZMmF15nFubFsyJLyF/zY4NhrISc8H/rbjgleqa8ybYL26iTS\ngfPCXe4U9f8fNFF2bSA06GTiB2R93q2B0zHeUYrpgF4XOGlIAqH+Ea4Vn+aOj6I0\npduh03idAgMBAAECggEBAJ+4SB/hYd1szrPZhkXtwhtp87pIObtuLhzYMzdjGFjM\nHdctfMDeNHKSNU+U4bMPFOZO2kcfLF2Ukb5X5WSzuDBMZNRnJOmtuJiEhJsM0JQR\nreREhLDfK3EWAAFkNV4corSpu/vIbEP87zuoRsPBVnHgQ/rM7y1kCORKL5bycwcw\n5BI4xhULKAu14LEcDL3+xDJo39w+WCFlxuP+6Bs7+vIeavs+AC3TJkA4kg2nyWd3\nW07xPjHl64f17icqsFhuFZ+VuSf5CAgQGWDbC7BHqRkDStUDSiiUiFushouKCLdK\nMpA0x4ogb2ZwfZDRhZHiLNAGe4QovYCcXWBydzuT0WECgYEA828Bo1JAHE5kdnsO\nE9+enH/yMcOKTRnuYPiXsFXNvqofc5tZiXJmVE/+EKv7LFmtUA6qqKC7FDek8TpP\nSkfXmSDAgfM6AdzT0YoHH23FRVewnFMEYumtogXsXJTyI5siBSJp16s9Rn/YwESt\nJqjW5+9Ck1dkU+UJCZ4lOw4HeGkCgYEA8zho2BKQTh3P/xcFcoTcunVZpRayVkHM\ng8Ef6RGGo4vM1oshQLvXyPqCmhAIf6j71I9WPqUwjmeGyaR7Hir0dbgTCm2fJPFW\nlxAvgbCISxEPz10RYBcR2umMSlJLfZfhqv1CyfU4vfCTbdOimgsz2039E3oLTbzg\neDe/mdzu2BUCgYEAleKjf4wFLWiXMtxRrqrhXjrpRPrBDPgKbmqh+1DZfawB8YyV\ndKublg4qwNkjrgsJS2G8cleE2M3qIR1l9LaHaSFhZqH79WmigkIaYJ+V9zwm4hm7\neaun3TsIbXjIHmRGbiLiSIiHEgFl0/x1IHiU2fnXZCFLBNzg06ssAVCCCQECgYA1\n4BfxTONkOlxZgAr33BBcySPLWuS0EK0xvjTIVtaBIbWFDJqYEUPyQ/NsFwMa7B6k\nbf/HrqW71ZjYz7Np8k/mR5kIJVIsR71Lhw1O6AC4yBW9dDsmEtYkrLkjuWj5cAxP\n6PvDaqtf/4tYt5l8D+Ezwem+R7l7RcxfNNIfTf4mJQKBgE57dnRx+Ijx7VHjJvjl\nX2jB/VSVGpK5OADykmmZ/wvHPlQcyzd+5kAIoJhSuY48CFeI1DOogR2p01LEFQEL\nj4AI5FqOOQwRJvNmfoKcKwO36tSxSEGSM8POKOsa21PG/gvDpJjVFo2hn5QcMHWn\nz5SjsgA/1YbXejubdLxT/3pl\n-----END PRIVATE KEY-----',
  api_v3_key = 'UDuLFDcmy5Eb6o0nTNZdu6ek4DDh4K8B',
  partner_key = 'T6m9iK73b0kn9g5v426MKfHQH7X8rKwb'
WHERE channel_code = 'WXPAY';

-- 支付宝：原 t_payment_app.app_config JSON 中的商户参数；returnUrl/notifyUrl 迁入 config_params
UPDATE t_payment_channel SET
  alipay_app_id = '9021000136667568',
  seller_id = '2088721034748965',
  merchant_private_key = 'MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCCDhLjo3lEFNcWdMnehWhHPams3KbmxpVJTxmIglvMLt6j207pSlWxSvacT3wFebXW4herg9RWhjTTh7KFwkxRWwzBVfp63YvEUbNsF09X1K6NqT2Kqz/w77l38lhQxpVau8odV3nHR+KhtDD2YJ9z8C2AzJLzKCD5CdK5coP7k+mRx3/mlOEj07oA7H6CrDkm4j4oasqEeLfbyJrXYcpPMi2hIHMXkR21fX5905v9BxwPhkVFsxHOABPYMWu5+uPpjRH1e/HKZpo5sinud05oWI0b1waMRZ9AGUEJnNZ3Yg9b9b2k1ycnCkApzy8cgQOgGhr6Rr9V7ieAsOn0YahLAgMBAAECggEAeImcvjj0GsKJ+zkxJDlXRbgD+7/iPL/O+0wBqUDQ3fSOyyVnBNetho2o9YTBuL1uaIPSVlfvxGXMrkT1k/1aCIkv0Dzk011kvgbPGZ6dHhVz1r4F2PERaTh2GJKXgf4bzSWBlSJPLwEULrU4MBGrl6QCOH7ir9UAgnC1SsW1R8QkCbgXchZkh9s3LddJ0Skb9Qu144yhT3tSxIMDL/ZJqANIR0Smv9tUuP2+DehqBH8OEapW4c00mt7OWT0jQ8H/8BaFV3j0q8SCn3cmKBFeUzh9H2UlHjiVUeF9mRmiEJUK8/N/li/WZbiWwRXOcRlno54+aRbvlwo+A8gWZF2n+QKBgQC6EIONzns/n0wSB87X0ZWUb7Y3Q6aReu/JvFwNuUA1wbS4bw7PTAijvB0daLXke142ujt6tnghyQHQuQU+5xzA5q8Q3Ooy8OZo9YgCRzNmU20CltEWcWijOt7ndCxpEVDeB7dY55yavVI8hgWX5LFI0t4Zwa8u+c+QL9C48DUVNQKBgQCy8DbydcZdRx2XSYZGtd0p+yR6lFucxpIFIYt/CFXcKGLKasF7Yhds5RKderp/I/1WZl0kwpbTv17HuvJqdoDX4qLXhvlh30P9Pbvvk7YhIv9oR9NqRtTTr0E40jALTouAuEaWs1f49C6AfrWJC26jgNHWpdeEkUQ6NN6DaP73fwKBgAsDRTYUfZkDbbY3fheqEQdrIUbeGzLLKvwuyOgLCfDkmTS9ZgwA/RXr4XFHLFTstGPa3ABkYnHletUGznetqDcGsF/4I2iGd6zIs5cm7bTlxTL9CD0i00WuC1l5t9M0MiwiGskJVGyYPhDVAem+oHul931gyGSoZo+rNNhtZ0btAoGAU2gM9K9ZKxl+/YnUARm8YVkjA9Arc8RLRAEC2M+11c0tX1Sroytx59xO9QDD9Yd9CszkFcJuM308XLUTUfSy0e5eIUBU9f3v3xbrhxy/BGsfyifQr/UcNx+1sxqmMl8GP5WlsZEfLHgFRPfK/npJtATTys26y5w6xTbnkTFbx1kCgYB+2wDnTWavXJkQErniXh3ifmC7qLcBNBpSUrI9eAtI4AwqKfBnp7d+wGuh05epecb1gzE76bWPqgIX7N5zmpZaQ/qZL+CgcGpU/7PN/qKGrqa1JEu8ZMaDhFys6MkIpQIGMElgnbM0cXvNrY8ReKvcGGysdtLnIBrp9BXcm+uWSg==',
  alipay_public_key = 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh9ivuRV+2eNbAzxXQ3bmx3wdhBHVaNQlhzv6wDXBMNhKG/AJDOAtYNbzPYJJPfySOrxyMlcTCvTGMS//Zn4yJHXx8IjJ4LSIguyy2BjDMwvDlF+TP7Xj6F/qtHWzuhztDnkLdAOwUx70Zyq1ajjzU5b2ku3J9WX5bwmnDpFGCVCwwG51mJYTd7Fwr4nE1qRZeMgGMhR7xR5Qdu1Nwx8Z+l1FCs47eZiirearT83/pwCRPD364SHq2uBJLtse9ozO7meBb8mzt6CDZKK6imEX1MKeVILbfE7GZAbtIAV4TLbvhB4VZuVxQrYmppPGhMpEiFDkpk0nFyNb7tz3HcHIQwIDAQAB'
WHERE channel_code = 'ALIPAY';

-- 3. t_payment_app 删除 app_config 列
ALTER TABLE t_payment_app DROP COLUMN app_config;

COMMIT;
