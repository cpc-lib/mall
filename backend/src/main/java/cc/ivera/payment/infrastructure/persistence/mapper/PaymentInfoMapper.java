package cc.ivera.payment.infrastructure.persistence.mapper;

import cc.ivera.payment.infrastructure.persistence.po.PaymentInfoPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentInfoMapper extends BaseMapper<PaymentInfoPO> {
}
