package cc.ivera.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefundQuotaReleasedEvent {
    private final String refundNo;
}
