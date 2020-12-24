package com.jinpei.graceful.shutdown.springboot1.state;

/**
 * 应用状态
 * Created by liuzhaoming on 2020/12/23
 */
public enum AppState {
    /**
     * The application is running and its internal state is correct.
     */
    RUNNING,

    /**
     * The application is running and its internal state is broken.
     */
    BROKEN,

    /**
     * The application is shutdowning and will refuse traffic.
     */
    SHUTDOWNING,

    /**
     * The application is starting and its internal state is not ready.
     */
    STARTING,
}
