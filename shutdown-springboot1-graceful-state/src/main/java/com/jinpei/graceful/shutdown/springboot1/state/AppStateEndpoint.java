package com.jinpei.graceful.shutdown.springboot1.state;

import lombok.Getter;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;

/**
 * 应用状态扩展点
 * Created by liuzhaoming on 2020/12/22
 */
@ConfigurationProperties(prefix = "endpoints.appstate")
public class AppStateEndpoint extends AbstractEndpoint<Map<String, Object>> {
    @Getter
    private volatile AppState appState = AppState.STARTING;

    public AppStateEndpoint() {
        super("appstate");
    }

    @Override
    public Map<String, Object> invoke() {
        return Collections.singletonMap("appState", appState);
    }

    public Map<String, Object> shutdown() {
        appState = AppState.SHUTDOWNING;
        return Collections.singletonMap("appState", appState);
    }

    public Map<String, Object> ready() {
        appState = AppState.RUNNING;
        return Collections.singletonMap("appState", appState);
    }

    public Map<String, Object> broken() {
        appState = AppState.BROKEN;
        return Collections.singletonMap("appState", appState);
    }

    public boolean isRunning() {
        return appState == AppState.RUNNING;
    }
}
