package com.jinpei.graceful.shutdown.springboot1.handler;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Undertow web容器优雅停机
 * Created by liuzhaoming on 2020/12/21
 */
@Slf4j
public class UndertowGracefulShutdown implements ApplicationListener<ContextClosedEvent> {

    private final UndertowGracefulShutdownWrapper gracefulShutdownWrapper;

    public UndertowGracefulShutdown(UndertowGracefulShutdownWrapper wrapper) {
        this.gracefulShutdownWrapper = wrapper;
    }

    public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
        try {
            log.info("Start to shutdown undertow thread pool");
            gracefulShutdownWrapper.getGracefulShutdownHandler().shutdown();
            if (!gracefulShutdownWrapper.getGracefulShutdownHandler().awaitShutdown(20 * 1000)) {
                log.warn("Undertow thread pool did not shutdown gracefully within 20 seconds. ");
            }
        } catch (Exception e) {
            log.warn("Fail to shutdown undertow thread pool", e);
        }
    }

    public static class UndertowGracefulShutdownWrapper implements HandlerWrapper {
        @Getter
        private GracefulShutdownHandler gracefulShutdownHandler;

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            if (gracefulShutdownHandler == null) {
                this.gracefulShutdownHandler = new GracefulShutdownHandler(handler);
            }
            return gracefulShutdownHandler;
        }
    }
}
