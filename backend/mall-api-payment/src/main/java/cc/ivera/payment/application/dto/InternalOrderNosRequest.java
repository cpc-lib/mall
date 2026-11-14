package cc.ivera.payment.application.dto;

import lombok.Data;

import java.util.Collection;

/**
 * 支付服务内部接口：按渠道 + 单号集合批量查询支付单。
 */
@Data
public class InternalOrderNosRequest {

    private String channel;

    private Collection<String> orderNos;
}
