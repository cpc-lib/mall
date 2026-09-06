package cc.ivera.controller;

import cc.ivera.controller.support.WxPayNotifyHandler;
import cc.ivera.entity.OrderInfo;
import cc.ivera.service.wxpay.WxPayBillFacade;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import cc.ivera.service.wxpay.WxPayRefundFacade;
import cc.ivera.vo.DownloadUrlVO;
import cc.ivera.vo.R;
import cc.ivera.vo.StringResultVO;
import cc.ivera.vo.WxPayJsapiVO;
import cc.ivera.vo.WxPayNativeVO;
import cc.ivera.vo.WxPayStatusVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 缺陷没有主动查询机制 都是采用微信通知来修改订单的状态 退款单的状态
 */
@CrossOrigin //跨域
@RestController
@RequestMapping("/api/wx-pay")
@Api(tags = "网站微信支付APIv3")
@Slf4j
@Validated
public class WxPayController {

    private final WxPayOrderFacade wxPayOrderFacade;

    private final WxPayRefundFacade wxPayRefundFacade;

    private final WxPayBillFacade wxPayBillFacade;

    private final WxPayNotifyHandler wxPayNotifyHandler;

    public WxPayController(
        WxPayOrderFacade wxPayOrderFacade,
        WxPayRefundFacade wxPayRefundFacade,
        WxPayBillFacade wxPayBillFacade,
        WxPayNotifyHandler wxPayNotifyHandler
    ) {
        this.wxPayOrderFacade = wxPayOrderFacade;
        this.wxPayRefundFacade = wxPayRefundFacade;
        this.wxPayBillFacade = wxPayBillFacade;
        this.wxPayNotifyHandler = wxPayNotifyHandler;
    }

    /**
     * Native下单表用户对接微信呢支付t_payment_info信息表 二维码页面一个接口调用后去调用本地t_order_info的状态
     *
     * @param productId
     * @return
     */
    @ApiOperation("调用统一下单API，生成支付二维码")
    @PostMapping("/native/{productId}")
    public R<WxPayNativeVO> nativePay(@PathVariable @Positive(message = "商品ID必须大于0") Long productId,
                                            @RequestParam(required = false) Long paymentAppId) {
        log.info("发起支付请求 v3，productId={}, paymentAppId={}", productId, paymentAppId);

        //返回支付二维码连接和订单号
        WxPayNativeVO map = wxPayOrderFacade.nativePay(productId, paymentAppId);

        return R.ok(map);
    }

    /**
     * 支付通知->需要在微信账号内配置
     * 微信支付通过支付通知接口将用户支付成功消息通知给商户
     * 特别注意测试需要开启花生壳类似工具
     * 修改订订单状态，记录支付记录
     */
    @ApiOperation("支付通知")
    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response) {
        return wxPayNotifyHandler.handle(request, response, wxPayOrderFacade::processOrder, "处理微信支付通知失败");
    }

    /**
     * TODO 用户取消订单
     * 做了两件时间 -> 主动调用微信支付单
     * 修改了本地之歌订单orderNo地状态为主动关闭
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("用户取消订单")
    @PostMapping("/cancel/{orderNo}")
    public R<?> cancel(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        log.info("取消订单");
        wxPayOrderFacade.cancelOrder(orderNo);
        return R.ok().setMessage("订单已取消");
    }

    /**
     * 查询订单 查询订单详情信息
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("查询订单：测试订单状态用")
    @GetMapping("/query/{orderNo}")
    public R<StringResultVO> queryOrder(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        log.info("查询订单");

        String result = wxPayOrderFacade.queryOrder(orderNo);
        StringResultVO vo = new StringResultVO();
        vo.setResult(result);
        return R.ok(vo).setMessage("查询成功");
    }

    /**
     * 主动查询微信支付状态，并在支付成功或已关闭时同步本地订单状态。
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("主动查询微信支付状态")
    @GetMapping("/check-order-status/{orderNo}")
    public R<WxPayStatusVO> checkOrderStatus(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        log.info("主动查询微信支付状态");

        WxPayStatusVO result = wxPayOrderFacade.queryPaymentStatus(orderNo);
        return R.ok(result).setMessage("查询成功");
    }

    /**
     * 查询退款
     *
     * @param refundNo
     * @return
     */
    @ApiOperation("查询退款：测试用")
    @GetMapping("/query-refund/{refundNo}")
    public R<StringResultVO> queryRefund(@PathVariable @NotBlank(message = "退款单号不能为空") @Size(max = 50, message = "退款单号长度不能超过50个字符") String refundNo) {
        log.info("查询退款");
        String result = wxPayRefundFacade.queryRefund(refundNo);
        StringResultVO vo = new StringResultVO();
        vo.setResult(result);
        return R.ok(vo).setMessage("查询成功");
    }

    /**
     * 退款结果通知 -需要在微信账号内配置
     * 退款状态改变后，微信会把相关退款结果发送给商户。
     */
    @ApiOperation("退款结果通知")
    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response) {
        log.info("退款通知执行");
        return wxPayNotifyHandler.handle(request, response, wxPayRefundFacade::processRefund, "处理微信退款通知失败");
    }

    @ApiOperation("获取账单url：测试用")
    @GetMapping("/querybill/{billDate}/{type}")
    public R<DownloadUrlVO> queryTradeBill(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "账单日期格式必须为yyyy-MM-dd") String billDate,
            @PathVariable @Pattern(regexp = "tradebill|fundflowbill", message = "微信账单类型只支持tradebill或fundflowbill") String type,
            @RequestParam(required = false) @Pattern(regexp = "ALL|SUCCESS|REFUND", message = "交易账单billType只支持ALL、SUCCESS或REFUND") String billType,
            @RequestParam(required = false) @Pattern(regexp = "BASIC|OPERATION|FEES", message = "资金账单accountType只支持BASIC、OPERATION或FEES") String accountType,
            @RequestParam(required = false) @Pattern(regexp = "GZIP", message = "tarType只支持GZIP") String tarType) {
        log.info("获取账单url");
        String downloadUrl = wxPayBillFacade.queryBill(billDate, type, billType, accountType, tarType);
        DownloadUrlVO vo = new DownloadUrlVO();
        vo.setDownloadUrl(downloadUrl);
        return R.ok(vo).setMessage("获取账单url成功");
    }

    @ApiOperation("下载账单")
    @GetMapping("/downloadbill/{billDate}/{type}")
    public R<StringResultVO> downloadBill(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "账单日期格式必须为yyyy-MM-dd") String billDate,
            @PathVariable @Pattern(regexp = "tradebill|fundflowbill", message = "微信账单类型只支持tradebill或fundflowbill") String type,
            @RequestParam(required = false) @Pattern(regexp = "ALL|SUCCESS|REFUND", message = "交易账单billType只支持ALL、SUCCESS或REFUND") String billType,
            @RequestParam(required = false) @Pattern(regexp = "BASIC|OPERATION|FEES", message = "资金账单accountType只支持BASIC、OPERATION或FEES") String accountType,
            @RequestParam(required = false) @Pattern(regexp = "GZIP", message = "tarType只支持GZIP") String tarType) {
        log.info("下载账单");
        String result = wxPayBillFacade.downloadBill(billDate, type, billType, accountType, tarType);
        StringResultVO vo = new StringResultVO();
        vo.setResult(result);
        return R.ok(vo);
    }

    @ApiOperation("调用统一下单API，生成预支付交易会话标识")
    @PostMapping("/jsapi")
    public R<WxPayJsapiVO> jsapiPay(OrderInfo orderInfo,
                                           @NotBlank(message = "openid不能为空") @Size(max = 128, message = "openid长度不能超过128个字符") String openid) {
        log.info("发起支付请求");
        WxPayJsapiVO map = wxPayOrderFacade.jsapiPay(orderInfo, openid);
        return R.ok(map);
    }

    @ApiOperation("微信支付回调")
    @PostMapping("/jsapi/notify/v1")
    public String jsapiNotifyV1(HttpServletRequest request, HttpServletResponse response) {
        return wxPayNotifyHandler.handle(request, response, wxPayOrderFacade::processOrder, "处理微信 JSAPI 支付通知失败");
    }

}
