package com.jinpei.graceful.shutdown.springboot1.state;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用状态配置参数
 * Created by liuzhaoming on 2020/12/23
 */
@ConfigurationProperties(prefix = "endpoints.appstate.check")
@Data
public class AppStateCheckProperties {
    /**
     * 启用业务状态健康检查
     */
    private boolean enable = true;

    /**
     * 业务状态检查时间间隔，单位毫秒
     */
    private int interval = 3000;

    /**
     * 业务状态检查rest接口路径
     */
    private String path = "/__check__";
}
