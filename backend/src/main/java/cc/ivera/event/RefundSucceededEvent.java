package cc.ivera.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class RefundSucceededEvent { private final String refundNo; }
