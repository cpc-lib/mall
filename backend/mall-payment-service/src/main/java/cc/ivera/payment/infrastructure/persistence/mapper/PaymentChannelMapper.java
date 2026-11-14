package cc.ivera.payment.infrastructure.persistence.mapper;

import cc.ivera.payment.infrastructure.persistence.po.PaymentChannelPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentChannelMapper extends BaseMapper<PaymentChannelPO> {
}
