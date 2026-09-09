package cc.ivera.payment.interfaces;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.interfaces.vo.AlipayFormVO;
import cc.ivera.payment.interfaces.vo.DownloadUrlVO;
import cc.ivera.payment.interfaces.vo.StringResultVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.util.MoneyUtils;
import cc.ivera.shared.web.R;
import com.alipay.api.AlipayConstants;
import com.alipay.api.internal.util.AlipaySignature;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/ali-pay")
@Api(tags = "网站支付宝支付")
@Slf4j
@Validated
public class AliPayController {

    private final AliPayService aliPayService;

    private final PaymentConfigGateway paymentConfigLoader;

    private final OrderInfoService orderInfoService;

    public AliPayController(
        AliPayService aliPayService,
        PaymentConfigGateway paymentConfigLoader,
        OrderInfoService orderInfoService
    ) {
        this.aliPayService = aliPayService;
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderInfoService = orderInfoService;
    }

    @ApiOperation("统一收单下单并支付页面接口的调用")
    @PostMapping("/trade/page/pay/{productId}")
    public R<AlipayFormVO> tradePagePay(@PathVariable @Positive(message = "商品ID必须大于0") Long productId,
                                        @RequestParam(required = false) Long paymentAppId) {
        log.info("统一收单下单并支付页面接口的调用，productId={}, paymentAppId={}", productId, paymentAppId);
        //支付宝开放平台接受 request 请求对象后
        // 会为开发者生成一个html 形式的 form表单，包含自动提交的脚本
        String formStr = aliPayService.tradeCreate(productId, paymentAppId);
        //我们将form表单字符串返回给前端程序，之后前端将会调用自动提交脚本，进行表单的提交
        //此时，表单会自动提交到action属性所指向的支付宝开放平台中，从而为用户展示一个支付页面
        AlipayFormVO vo = new AlipayFormVO();
        vo.setFormStr(formStr);
        return R.ok(vo);
    }

    @ApiOperation("支付通知")
    @PostMapping("/trade/notify")
    public String tradeNotify(@RequestParam Map<String, String> params) {
        log.info("支付通知正在执行");
        log.info("通知参数 ===> {}", params);

        String result = "failure";

        try {
            //按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，
            //1 商户需要验证该通知数据中的 out_trade_no 是否为商户系统中创建的订单号
            String outTradeNo = params.get("out_trade_no");
            OrderInfo order = orderInfoService.getOrderByOrderNo(outTradeNo);
            if (order == null) {
                log.error("订单不存在");
                return result;
            }

            PaymentAppConfig payConfig = resolveAliPayConfig(order);

            //异步通知验签：根据订单绑定的支付应用读取支付宝公钥，避免多应用场景错验。
            boolean signVerified = AlipaySignature.rsaCheckV1(
                params,
                payConfig.getAlipayPublicKey(),
                AlipayConstants.CHARSET_UTF8,
                AlipayConstants.SIGN_TYPE_RSA2);

            if (!signVerified) {
                log.error("支付成功异步通知验签失败！");
                return result;
            }

            log.info("支付成功异步通知验签成功！");

            //2 判断 total_amount 是否确实为该订单的实际金额（即商户订单创建时的金额）
            String totalAmount = params.get("total_amount");
            int totalAmountInt = MoneyUtils.yuanToCents(totalAmount);
            if (order.getTotalFee() == null) {
                log.error("订单金额为空");
                return result;
            }
            int totalFeeInt = order.getTotalFee().intValue();
            if (totalAmountInt != totalFeeInt) {
                log.error("金额校验失败");
                return result;
            }

            //3 校验通知中的 seller_id（或者 seller_email) 是否为 out_trade_no 这笔单据的对应的操作方
            String sellerId = params.get("seller_id");
            String sellerIdProperty = payConfig.getSellerId();
            if (sellerId == null || !sellerId.equals(sellerIdProperty)) {
                log.error("商家pid校验失败");
                return result;
            }

            //4 验证 app_id 是否为该商户本身
            String appId = params.get("app_id");
            String appIdProperty = payConfig.getAlipayAppId();
            if (appId == null || !appId.equals(appIdProperty)) {
                log.error("appid校验失败");
                return result;
            }

            //在支付宝的业务通知中，只有交易通知状态为 TRADE_SUCCESS时，
            // 支付宝才会认定为买家付款成功。
            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus)) {
                log.error("支付未成功");
                return result;
            }

            //处理业务 修改订单状态 记录支付日志
            aliPayService.processOrder(params);

            //校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            result = "success";
        } catch (Exception e) {
            log.error("处理支付宝异步通知失败", e);
        }
        return result;
    }

    /**
     * 用户取消订单
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("用户取消订单")
    @PostMapping("/trade/close/{orderNo}")
    public R<?> cancel(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        log.info("取消订单");
        aliPayService.cancelOrder(orderNo);
        return R.ok().setMessage("订单已取消");
    }

    /**
     * 查询订单
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("查询订单：测试订单状态用")
    @GetMapping("/trade/query/{orderNo}")
    public R<StringResultVO> queryOrder(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        log.info("查询订单");

        String result = aliPayService.queryOrder(orderNo);
        StringResultVO vo = new StringResultVO();
        vo.setResult(result);
        return R.ok(vo).setMessage("查询成功");
    }

    /**
     * 查询退款
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("查询退款：测试用")
    @GetMapping("/trade/fastpay/refund/{refundNo}")
    public R<StringResultVO> queryRefund(@PathVariable @NotBlank(message = "退款单号不能为空") @Size(max = 50, message = "退款单号长度不能超过50个字符") String refundNo) {
        log.info("查询退款");

        String result = aliPayService.queryRefund(refundNo);
        StringResultVO vo = new StringResultVO();
        vo.setResult(result);
        return R.ok(vo).setMessage("查询成功");
    }

    /**
     * 根据账单类型和日期获取账单url地址
     *
     * @param billDate
     * @param type
     * @return
     */
    @ApiOperation("获取账单url")
    @GetMapping("/bill/downloadurl/query/{billDate}/{type}")
    public R<DownloadUrlVO> queryTradeBill(
        @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "账单日期格式必须为yyyy-MM-dd") String billDate,
        @PathVariable @Pattern(regexp = "trade|signcustomer", message = "支付宝账单类型只支持trade或signcustomer") String type) {
        log.info("获取账单url");
        String downloadUrl = aliPayService.queryBill(billDate, type);
        DownloadUrlVO vo = new DownloadUrlVO();
        vo.setDownloadUrl(downloadUrl);
        return R.ok(vo).setMessage("获取账单url成功");
    }

    private PaymentAppConfig resolveAliPayConfig(OrderInfo order) {
        PaymentAppConfig config = order == null || order.getPaymentAppId() == null
            ? paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigGateway.CHANNEL_ALIPAY)
            : paymentConfigLoader.getAppConfig(order.getPaymentAppId());
        if (config != null) {
            return config;
        }
        throw new BizException("支付宝支付渠道未配置或未启用");
    }

}
