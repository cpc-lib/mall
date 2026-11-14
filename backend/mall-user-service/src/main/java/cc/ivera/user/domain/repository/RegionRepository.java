package cc.ivera.user.domain.repository;

import cc.ivera.user.domain.model.Region;

import java.util.List;

public interface RegionRepository {

    /**
     * 全部区域：按 parentId、sort、id 升序，供构建省市区树。
     */
    List<Region> listAllOrdered();
}
