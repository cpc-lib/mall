package cc.ivera.controller;

import cc.ivera.config.WxPayConfig;
import cc.ivera.vo.R;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "测试控制器")
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final WxPayConfig wxPayConfig;

    public TestController(
        WxPayConfig wxPayConfig
    ) {
        this.wxPayConfig = wxPayConfig;
    }

    @GetMapping
    public R<String> getWxPayConfig() {
        return R.ok(wxPayConfig.getMchId());
    }
}
