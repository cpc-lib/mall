package cc.ivera.shared.infrastructure.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConsumerDedupeMapper extends BaseMapper<ConsumerDedupePO> {
}
