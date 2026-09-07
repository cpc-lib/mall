package cc.ivera.controller;

import cc.ivera.entity.Region;
import cc.ivera.mapper.RegionMapper;
import cc.ivera.vo.R;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 省市区三级区域树（公共只读，无需登录）。 */
@RestController
@RequestMapping("/api/regions")
@CrossOrigin
public class RegionController {
    private final RegionMapper mapper;

    public RegionController(RegionMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        QueryWrapper<Region> q = new QueryWrapper<>();
        q.orderByAsc("parent_id").orderByAsc("sort").orderByAsc("id");
        List<Region> all = mapper.selectList(q);
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
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
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
