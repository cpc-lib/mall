package cc.ivera.bill.interfaces.vo;

import cc.ivera.bill.domain.model.BillVerificationItem;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BillVerificationItemVO {

    private String field;
    private String channelValue;
    private String localValue;
    private Boolean matched;

    public static BillVerificationItemVO from(BillVerificationItem entity) {
        if (entity == null) {
            return null;
        }
        BillVerificationItemVO vo = new BillVerificationItemVO();
        vo.setField(entity.getField());
        vo.setChannelValue(entity.getChannelValue());
        vo.setLocalValue(entity.getLocalValue());
        vo.setMatched(entity.getMatched());
        return vo;
    }

    public static List<BillVerificationItemVO> from(List<BillVerificationItem> entities) {
        List<BillVerificationItemVO> result = new ArrayList<>();
        if (entities != null) {
            for (BillVerificationItem entity : entities) {
                result.add(from(entity));
            }
        }
        return result;
    }
}
