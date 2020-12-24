package com.jinpei.graceful.shutdown.springboot1.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


/**
 * Tomcat web容器优雅停机
 * Created by liuzhaoming on 2020/12/21
 */
@Slf4j
public class TomcatGracefulShutdown implements TomcatConnectorCustomizer, ApplicationListener<ContextClosedEvent> {
    private volatile Connector connector;

    public void customize(Connector connector) {
        this.connector = connector;
    }

    public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
        this.connector.pause();
        Executor executor = this.connector.getProtocolHandler().getExecutor();
        if (executor instanceof ThreadPoolExecutor) {
            try {
                log.info("Start to shutdown tomcat thread pool.");
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                threadPoolExecutor.shutdown();
                if (!threadPoolExecutor.awaitTermination(20, TimeUnit.SECONDS)) {
                    log.warn("Tomcat thread pool did not shutdown gracefully within 20 seconds. ");
                }
            } catch (InterruptedException e) {
                log.warn("Fail to shut down tomcat thread pool ", e);
            }
        }
    }
}
