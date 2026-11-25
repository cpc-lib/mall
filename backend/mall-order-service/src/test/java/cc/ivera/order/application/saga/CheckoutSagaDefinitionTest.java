package cc.ivera.order.application.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 守护下单 Saga 的步骤顺序：必须先预占库存再落单，
 * 否则库存不足时会短暂出现可见的 WAIT_PAY 订单，违反“不允许先生成有效订单再补偿扣库存”的约束。
 */
class CheckoutSagaDefinitionTest {

    @Test
    void reservesInventoryBeforeCreatingOrder() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/statelang/order-checkout.json")) {
            assertNotNull(in, "未找到 order-checkout.json");
            JsonNode root = new ObjectMapper().readTree(in);
            assertEquals("ReserveInventory", root.path("StartState").asText());

            JsonNode states = root.path("States");
            assertEquals("CreateOrder", states.path("ReserveInventory").path("Next").asText());
            assertEquals("ReleaseInventory", states.path("ReserveInventory").path("CompensateState").asText());
            assertEquals("ClearCart", states.path("CreateOrder").path("Next").asText());
            assertEquals("CancelOrder", states.path("CreateOrder").path("CompensateState").asText());
        }
    }
}
