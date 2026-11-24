package cc.ivera.user.application.impl;

import cc.ivera.user.application.RegionService;
import cc.ivera.user.domain.model.Region;
import cc.ivera.user.domain.repository.RegionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegionServiceImpl implements RegionService {
    private final RegionRepository regionRepository;

    private volatile List<Region> cachedRegions;

    public RegionServiceImpl(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @Override
    public List<Region> listAllOrdered() {
        List<Region> local = cachedRegions;
        if (local == null) {
            synchronized (this) {
                local = cachedRegions;
                if (local == null) {
                    local = regionRepository.listAllOrdered();
                    cachedRegions = local;
                }
            }
        }
        return local;
    }
}
