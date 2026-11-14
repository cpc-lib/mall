package cc.ivera.order.infrastructure.remote;

import cc.ivera.order.infrastructure.remote.client.UserServiceClient;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.user.domain.model.ShippingAddress;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 收货地址远程适配器：用户服务是地址数据的唯一写者，订单只读取快照。
 */
@Component
public class UserRemoteAdapter implements ShippingAddressService {
    private static final String SERVICE = "用户服务";

    private final UserServiceClient client;

    public UserRemoteAdapter(UserServiceClient client) {
        this.client = client;
    }

    @Override
    public List<ShippingAddress> listByUser(Long userId) {
        return RemoteResponseUnwrapper.unwrap(client.listAddresses(userId), SERVICE);
    }

    @Override
    public ShippingAddress getById(Long userId, Long id) {
        return RemoteResponseUnwrapper.unwrap(client.getAddress(id, userId), SERVICE);
    }

    @Override
    public ShippingAddress create(Long userId, ShippingAddress addr) {
        return RemoteResponseUnwrapper.unwrap(client.createAddress(userId, addr), SERVICE);
    }

    @Override
    public ShippingAddress update(Long userId, ShippingAddress addr) {
        return RemoteResponseUnwrapper.unwrap(client.updateAddress(addr.getId(), userId, addr), SERVICE);
    }

    @Override
    public void delete(Long userId, Long id) {
        RemoteResponseUnwrapper.unwrap(client.deleteAddress(id, userId), SERVICE);
    }

    @Override
    public void setDefault(Long userId, Long id) {
        RemoteResponseUnwrapper.unwrap(client.setDefaultAddress(id, userId), SERVICE);
    }

    @Override
    public ShippingAddress defaultOf(Long userId) {
        return RemoteResponseUnwrapper.unwrap(client.defaultAddress(userId), SERVICE);
    }
}
