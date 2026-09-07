package cc.ivera.controller;

import cc.ivera.dto.admin.UserStatusRequest;
import cc.ivera.entity.UserAccount;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.UserAccountMapper;
import cc.ivera.vo.PageVO;
import cc.ivera.vo.R;
import cc.ivera.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 管理员用户管理：用户列表（分页+搜索，仅基础状态）+ 用户详情（含在线状态）+ 禁用/启用。
 */
@RestController @RequestMapping("/api/admin/users") @CrossOrigin
@Api(tags = "管理员用户管理API")
public class AdminUserController {
    private final UserAccountMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String VERSION_PREFIX = "auth:token_version:";

    public AdminUserController(UserAccountMapper userMapper, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper; this.redisTemplate = redisTemplate;
    }

    @ApiOperation("用户列表（分页 + 用户名关键字搜索，不含在线状态）")
    @GetMapping
    public R<PageVO<UserVO>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String keywordLike = (keyword != null && !keyword.trim().isEmpty()) ? "%" + keyword.trim() + "%" : null;
        long total = userMapper.countAdminUsers(keywordLike);
        List<UserAccount> users = userMapper.selectAdminUsers(keywordLike, safeSize, (long) (safePage - 1) * safeSize);
        List<UserVO> records = new ArrayList<>();
        for (UserAccount u : users) records.add(toRow(u));
        PageVO<UserVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(records);
        return R.ok(result);
    }

    @ApiOperation("用户详情（含在线状态）")
    @GetMapping("/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        UserAccount u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        UserVO vo = toRow(u);
        vo.setOnline(scanOnlineUserIds().contains(u.getId()));
        return R.ok(vo);
    }

    @ApiOperation("用户状态切换（禁用即强制下线）")
    @PutMapping("/{id}/status")
    public R<UserVO> setStatus(@PathVariable Long id, @RequestBody UserStatusRequest body) {
        String status = body == null ? null : body.getUserStatus();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) throw new BizException("用户状态仅支持 ENABLED / DISABLED");
        UserAccount u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        if ("ROLE_ADMIN".equals(u.getRole())) throw new BizException("仅允许操作普通用户账号");
        if (!status.equals(u.getUserStatus())) {
            u.setUserStatus(status);
            userMapper.updateById(u);
        }
        // 禁用即踢下线：版本自增令存量 Access Token 全部失效，Refresh Token 因版本与状态双重校验失效（登录/刷新本就拒绝禁用用户）
        if ("DISABLED".equals(status)) redisTemplate.opsForValue().increment(VERSION_PREFIX + u.getId());
        UserVO row = new UserVO();
        row.setId(u.getId());
        row.setUsername(u.getUsername());
        row.setRole(u.getRole());
        row.setUserStatus(u.getUserStatus());
        return R.ok(row);
    }

    private UserVO toRow(UserAccount u) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRole(u.getRole());
        vo.setUserStatus(u.getUserStatus());
        vo.setCreateTime(u.getCreateTime());
        vo.setUpdateTime(u.getUpdateTime());
        return vo;
    }

    // 扫描 Redis refresh token，提取在线 userId 集合
    private Set<Long> scanOnlineUserIds() {
        Set<Long> onlineIds = new HashSet<>();
        try {
            Set<String> keys = redisTemplate.keys(REFRESH_PREFIX + "*");
            if (keys != null) {
                List<String> values = redisTemplate.opsForValue().multiGet(keys);
                if (values != null) {
                    for (String v : values) {
                        if (v != null) {
                            String[] parts = v.split(":", 2);
                            try { onlineIds.add(Long.valueOf(parts[0])); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return onlineIds;
    }
}
