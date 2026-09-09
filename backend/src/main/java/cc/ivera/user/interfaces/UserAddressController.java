package cc.ivera.user.interfaces;

import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.user.domain.model.ShippingAddress;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户收货地址管理（需登录，仅本人可操作）。
 */
@RestController
@RequestMapping("/api/user/address")
@CrossOrigin
public class UserAddressController {
    private final ShippingAddressService service;

    public UserAddressController(ShippingAddressService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public R<List<ShippingAddress>> list() {
        return R.ok(service.listByUser(AuthContext.userId()));
    }

    @GetMapping("/default")
    public R<ShippingAddress> defaultAddr() {
        return R.ok(service.defaultOf(AuthContext.userId()));
    }

    @PostMapping
    public R<ShippingAddress> create(@RequestBody ShippingAddress addr) {
        Long userId = AuthContext.userId();
        if (addr.getReceiverName() == null || addr.getReceiverName().trim().isEmpty())
            return R.error(ErrorCode.PARAM_ERROR, "收货人姓名必填");
        if (addr.getReceiverPhone() == null || addr.getReceiverPhone().trim().isEmpty())
            return R.error(ErrorCode.PARAM_ERROR, "收货人电话必填");
        if (addr.getProvince() == null || addr.getProvince().trim().isEmpty())
            return R.error(ErrorCode.PARAM_ERROR, "省份必填");
        if (addr.getCity() == null || addr.getCity().trim().isEmpty())
            return R.error(ErrorCode.PARAM_ERROR, "城市必填");
        if (addr.getDistrict() == null || addr.getDistrict().trim().isEmpty())
            return R.error(ErrorCode.PARAM_ERROR, "区县必填");
        if (addr.getDetail() == null || addr.getDetail().trim().isEmpty())
            return R.error(ErrorCode.PARAM_ERROR, "详细地址必填");
        return R.ok(service.create(userId, addr));
    }

    @PutMapping("/{id}")
    public R<ShippingAddress> update(@PathVariable Long id, @RequestBody ShippingAddress addr) {
        addr.setId(id);
        return R.ok(service.update(AuthContext.userId(), addr));
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        service.delete(AuthContext.userId(), id);
        return R.ok();
    }

    @PutMapping("/{id}/default")
    public R<?> setDefault(@PathVariable Long id) {
        service.setDefault(AuthContext.userId(), id);
        return R.ok();
    }
}
