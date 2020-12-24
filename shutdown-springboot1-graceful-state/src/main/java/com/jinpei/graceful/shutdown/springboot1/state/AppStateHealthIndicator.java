package com.jinpei.graceful.shutdown.springboot1.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

/**
 * 应用状态检查rest接口，需要走业务端口给nginx调用，默认springboot监控走的是管理端口
 * Created by liuzhaoming on 2020/12/23
 */
@RequestMapping
@Slf4j
public class AppStateHealthIndicator {

    @Autowired(required = false)
    private HealthEndpoint healthEndpoint;

    @Autowired(required = false)
    private AppStateEndpoint appStateEndpoint;

    @Autowired
    private AppStateCheckProperties appStateCheckProperties;

    @RequestMapping(path = "${endpoints.appstate.check.path:/__check__}", method = RequestMethod.GET)
    public void check(HttpServletResponse response) {
        if (null != appStateEndpoint && !appStateEndpoint.isRunning()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    /**
     * 应用启动时，启动一个线程对应用的健康状态进行检查；如果健康检查通过，则将应用状态改为ready
     */
    @PostConstruct
    public void init() {
        if (null != healthEndpoint && null != appStateEndpoint) {
            //如果开启了健康检查，那么启动线程对健康检查进行检测，监控检测通过会自动更新app state状态为ready
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        Health health = healthEndpoint.invoke();
                        if (health.getStatus() == Status.UP) {
                            appStateEndpoint.ready();
                            return;
                        }
                    } catch (Exception e) {
                        log.debug("Invoke health endpoint error ", e);
                    } finally {
                        try {
                            Thread.sleep(appStateCheckProperties.getInterval());
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            });
            thread.setDaemon(true);
            thread.setName("AppStateReadyChecker");
            thread.start();
        }
    }
}