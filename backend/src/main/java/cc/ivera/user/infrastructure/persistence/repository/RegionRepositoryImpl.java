package cc.ivera.user.infrastructure.persistence.repository;

import cc.ivera.user.domain.model.Region;
import cc.ivera.user.domain.repository.RegionRepository;
import cc.ivera.user.infrastructure.persistence.converter.RegionPOConverter;
import cc.ivera.user.infrastructure.persistence.mapper.RegionMapper;
import cc.ivera.user.infrastructure.persistence.po.RegionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class RegionRepositoryImpl implements RegionRepository {

    private final RegionMapper regionMapper;

    public RegionRepositoryImpl(RegionMapper regionMapper) {
        this.regionMapper = regionMapper;
    }

    @Override
    public List<Region> listAllOrdered() {
        return regionMapper.selectList(new LambdaQueryWrapper<RegionPO>()
                .orderByAsc(RegionPO::getParentId)
                .orderByAsc(RegionPO::getSort)
                .orderByAsc(RegionPO::getId))
            .stream().map(RegionPOConverter::toDomain).collect(Collectors.toList());
    }
}
