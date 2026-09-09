package cc.ivera.user.interfaces;

import cc.ivera.shared.web.R;
import cc.ivera.user.domain.model.Region;
import cc.ivera.user.domain.repository.RegionRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 省市区三级区域树（公共只读，无需登录）。
 */
@RestController
@RequestMapping("/api/regions")
@CrossOrigin
public class RegionController {
    private final RegionRepository regionRepository;

    public RegionController(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        List<Region> all = regionRepository.listAllOrdered();
        // 构建树
        Map<Long, List<Region>> byParent = all.stream().collect(Collectors.groupingBy(r -> r.getParentId()));
        List<Map<String, Object>> root = new ArrayList<>();
        for (Region r : all) {
            if (r.getParentId() != 0) continue;
            root.add(build(r, byParent));
        }
        return R.ok(root);
    }

    private Map<String, Object> build(Region node, Map<Long, List<Region>> byParent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", node.getCode());
        m.put("label", node.getName());
        List<Region> children = byParent.get(node.getId());
        if (children != null && !children.isEmpty()) {
            List<Map<String, Object>> kids = new ArrayList<>();
            for (Region c : children) kids.add(build(c, byParent));
            m.put("children", kids);
        }
        return m;
    }
}
