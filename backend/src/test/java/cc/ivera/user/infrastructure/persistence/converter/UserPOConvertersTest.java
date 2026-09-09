package cc.ivera.user.infrastructure.persistence.converter;

import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.domain.model.Region;
import cc.ivera.user.domain.model.ShippingAddress;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.infrastructure.persistence.po.PasswordResetRequestPO;
import cc.ivera.user.infrastructure.persistence.po.RegionPO;
import cc.ivera.user.infrastructure.persistence.po.ShippingAddressPO;
import cc.ivera.user.infrastructure.persistence.po.UserAccountPO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * user 上下文 PO ↔ 领域对象手写 Converter 往返映射测试：全字段等值，不连数据库。
 */
class UserPOConvertersTest {

    @Test
    void userAccountRoundtripMapsAllFields() {
        UserAccount domain = new UserAccount();
        domain.setId(1L);
        domain.setUsername("alice");
        domain.setPasswordHash("hash-abc");
        domain.setPasswordSalt("salt-xyz");
        domain.setRole("ROLE_USER");
        domain.setUserStatus("ENABLED");
        domain.setCreateTime(new Date(1_000L));
        domain.setUpdateTime(new Date(2_000L));

        UserAccount back = UserAccountPOConverter.toDomain(UserAccountPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getUsername(), back.getUsername());
        assertEquals(domain.getPasswordHash(), back.getPasswordHash());
        assertEquals(domain.getPasswordSalt(), back.getPasswordSalt());
        assertEquals(domain.getRole(), back.getRole());
        assertEquals(domain.getUserStatus(), back.getUserStatus());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void userAccountConverterNullSafe() {
        assertNull(UserAccountPOConverter.toPO(null));
        assertNull(UserAccountPOConverter.toDomain(null));
    }

    @Test
    void shippingAddressRoundtripMapsAllFields() {
        ShippingAddress domain = new ShippingAddress();
        domain.setId(2L);
        domain.setUserId(7L);
        domain.setReceiverName("张三");
        domain.setReceiverPhone("13800000000");
        domain.setProvince("浙江省");
        domain.setCity("杭州市");
        domain.setDistrict("西湖区");
        domain.setDetail("文三路 100 号");
        domain.setIsDefault("1");
        domain.setCreateTime(new Date(3_000L));
        domain.setUpdateTime(new Date(4_000L));

        ShippingAddress back = ShippingAddressPOConverter.toDomain(ShippingAddressPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getUserId(), back.getUserId());
        assertEquals(domain.getReceiverName(), back.getReceiverName());
        assertEquals(domain.getReceiverPhone(), back.getReceiverPhone());
        assertEquals(domain.getProvince(), back.getProvince());
        assertEquals(domain.getCity(), back.getCity());
        assertEquals(domain.getDistrict(), back.getDistrict());
        assertEquals(domain.getDetail(), back.getDetail());
        assertEquals(domain.getIsDefault(), back.getIsDefault());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void shippingAddressConverterNullSafe() {
        assertNull(ShippingAddressPOConverter.toPO(null));
        assertNull(ShippingAddressPOConverter.toDomain(null));
    }

    @Test
    void passwordResetRequestRoundtripMapsAllFields() {
        PasswordResetRequest domain = new PasswordResetRequest();
        domain.setId(3L);
        domain.setUsername("bob");
        domain.setRemark("忘记密码");
        domain.setStatus("PENDING");
        domain.setAdminRemark(null);
        domain.setHandledBy(99L);
        domain.setCreateTime(new Date(5_000L));
        domain.setUpdateTime(new Date(6_000L));

        PasswordResetRequest back =
            PasswordResetRequestPOConverter.toDomain(PasswordResetRequestPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getUsername(), back.getUsername());
        assertEquals(domain.getRemark(), back.getRemark());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getAdminRemark(), back.getAdminRemark());
        assertEquals(domain.getHandledBy(), back.getHandledBy());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void passwordResetRequestConverterNullSafe() {
        assertNull(PasswordResetRequestPOConverter.toPO(null));
        assertNull(PasswordResetRequestPOConverter.toDomain(null));
    }

    @Test
    void regionRoundtripMapsAllFields() {
        Region domain = new Region();
        domain.setId(4L);
        domain.setParentId(0L);
        domain.setCode("330000");
        domain.setName("浙江省");
        domain.setLevel(1);
        domain.setSort(10);

        Region back = RegionPOConverter.toDomain(RegionPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getParentId(), back.getParentId());
        assertEquals(domain.getCode(), back.getCode());
        assertEquals(domain.getName(), back.getName());
        assertEquals(domain.getLevel(), back.getLevel());
        assertEquals(domain.getSort(), back.getSort());
    }

    @Test
    void regionConverterNullSafe() {
        assertNull(RegionPOConverter.toPO(null));
        assertNull(RegionPOConverter.toDomain(null));
    }
}
