# 优雅停机

## 1 单应用优雅停机的定义

优雅停机是指关闭应用程序时，在规定的超时时间范围内，允许进行中的请求完成，拒绝新的请求进入。这将使应用在请求处理方面保持一致，即没有未处理请求，每一个请求都被处理（完成或拒绝）。优雅停机包含三个要素：

1. 如何关闭应用程序
2. 完成正在进行中的请求
3. 新的请求将被拒绝

### 1.1 应用关闭命令-Linux Kill命令

kill 命令常用的信号选项:

1. kill -2 pid 向指定 pid 发送 SIGINT 中断信号，等同于 ctrl+c。
2. kill -9 pid，向指定 pid 发送 SIGKILL 立即终止信号。
3. kill -15 pid，向指定 pid 发送 SIGTERM 终止信号。
4. kill pid 等同于 kill 15 pid

SIGINT/SIGKILL/SIGTERM 信号的区别:

1. SIGINT (ctrl+c) 信号 (信号编号为 2)，信号会被当前进程树接收到，也就说，不仅当前进程会收到该信号，而且它的子进程也会收到。
2. SIGKILL 信号 (信号编号为 9)，程序不能捕获该信号，最粗暴最快速结束程序的方法。
3. SIGTERM 信号 (信号编号为 15)，信号会被当前进程接收到，但它的子进程不会收到，如果当前进程被 kill 掉，它的的子进程的父进程将变成 init 进程 (init 进程是那个 pid 为 1 的进程)

一般要结束某个进程，我们应该优先使用 kill pid ，而不是 kill -9 pid。如果对应程序提供优雅关闭机制的话, 在完全退出之前, 先可以做一些善后处理。

### 1.2 JAVA 对于应用关闭的底层支持

JAVA 语言底层有机制能捕获到 OS 的 SIGINT (kill -2 / ctrl + c) / SIGTERM (kill -15)信号。通过 Runtime.getRuntime().addShutdownHook() 向 JVM 中注册一个 ShutdownHook 线程，当 JVM 收到停止信号后，该线程将被激活运行。可以在Hook线程向其他线程发出中断指令，然后等待其他线程执行完毕，进而优雅地关闭整个程序。
[示例代码](./shutdown-springboot1/src/main/java/com/jinpei/graceful/shutdown/springboot1/JavaShutdownHookDemo.java)：

```java
public class JavaShutdownHookDemo {
    public static void main(String[] args) {
        System.out.println("1. MainThread 启动");

        final Thread mainThread = Thread.currentThread();
        //注册Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("2. 接收到关闭信号");
            // 给主线程发送中断信号，正常需要在这个地方触发业务关闭逻辑，比如spring context的close
            mainThread.interrupt();
            try {
                // 等待主线程正常执行完成
                mainThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("5. 优雅关闭完成");
        }));

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            // 中断响应，处理本业务关闭逻辑
            System.out.println("3. 主线程被中断，处理中断逻辑");
        }

        System.out.println("4. Main Thread 执行完毕");
    }
}
```

正常情况下, 程序需要运行 30 秒，程序的输出是：
![rgIW6I.png](https://s3.ax1x.com/2020/12/24/rgIW6I.png)

如果在程序启动后, 按下 Ctrl+C, 程序很快就结束了, 最终的输出是:
![rgIvn0.md.png](https://s3.ax1x.com/2020/12/24/rgIvn0.md.png)

ShutdownHook 的使用注意点：

1. ShutdownHook 本质是线程，因此调用是不保证顺序的
2. ShutdownHook 是JVM结束前调用的线程，所以该线程中的方法应尽量短，并且保证不能发生死锁的情况，否则也会阻止JVM的正常退出
3. ShutdownHook 中不能执行 System.exit()，否则会导致虚拟机卡住，而不得不强行杀死进程

### 1.3 Spring 添加 ShutdownHook

Spring 框架中添加 ShutdownHook 有两种方法：

1. 实现DisposableBean接口，实现destroy方法

```java
@Component
public class HookExample implements DisposableBean {
    @Override
    public void destroy() throws Exception {
        //具体关闭逻辑
    }
}
```

2. 使用@PreDestroy注解

```java
@Component
public class HookExample {
    @PreDestroy
    public void shutdown() throws Exception {
        //具体关闭逻辑
    }
}
```

@PreDestroy 比 DisposableBean 先执行。

通过分析 Spring 的源码可知，Spring 在 AbstractApplicationContext 类中添加了Java ShutdownHook，源码如下：

```java
public void registerShutdownHook() {
    if (this.shutdownHook == null) {
        // No shutdown hook registered yet.
        this.shutdownHook = new Thread() {
            @Override
            public void run() {
                synchronized (startupShutdownMonitor) {
                    doClose();
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }
}

protected void doClose() {
    if (this.active.get() && this.closed.compareAndSet(false, true)) {
        if (logger.isInfoEnabled()) {
            logger.info("Closing " + this);
        }

        LiveBeansView.unregisterApplicationContext(this);

        try {
            // Publish shutdown event.
            publishEvent(new ContextClosedEvent(this));
        }
        catch (Throwable ex) {
            logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
        }

        // Stop all Lifecycle beans, to avoid delays during individual destruction.
        try {
            getLifecycleProcessor().onClose();
        }
        catch (Throwable ex) {
            logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
        }

        // Destroy all cached singletons in the context's BeanFactory.
        destroyBeans();

        // Close the state of this context itself.
        closeBeanFactory();

        // Let subclasses do some final clean-up if they wish...
        onClose();

        this.active.set(false);
    }
}
    
```

## 2 Spring Boot 2.3 之前版本 Actuator Shutdown

在 Spring Boot 2.3 之前版本是没有优雅停机的功能，见：[https://github.com/spring-projects/spring-boot/issues/4657](https://github.com/spring-projects/spring-boot/issues/4657)。Spring Boot Actuator 提供的 Shutdown 并不能实现优雅停机。

### 2.1 代码测试

测试代码见：[shutdown-springboot1](/shutdown-springboot1)，测试代码使用的 Spring Boot 版本为 1.5.22。

1. 启动应用后，访问 [http://localhost:8081/api/hello?serialNo=111&sleepSeconds=100](http://localhost:8081/api/hello?serialNo=111&sleepSeconds=100)，此接口会阻塞100秒。

2. 通过 Shutdown Endpoint 关闭应用：
![](https://s3.ax1x.com/2020/12/24/r2cQDe.png)

3. 关闭后，请求1会报错，得不到后端的返回值
![](https://s3.ax1x.com/2020/12/24/r2crUs.png)

### 2.2 源码分析

通过查看源码得知，2.3 版本之前的 Shutdown 只是关闭 Spring 上下文。
首先查看 ShutdownMvcEndpoint 类，Shutdown 请求调用的这个类的 invoke 方法

```java
@PostMapping(produces = { ActuatorMediaTypes.APPLICATION_ACTUATOR_V1_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE })
@ResponseBody
@Override
public Object invoke() {
    if (!getDelegate().isEnabled()) {
        return new ResponseEntity<Map<String, String>>(
                Collections.singletonMap("message", "This endpoint is disabled"), HttpStatus.NOT_FOUND);
    }
    return super.invoke();
}
```

最终是调用 ShutdownEndpoint 的 invoke 方法。ShutdownEndpoint 源码：

```java
public Map<String, Object> invoke() {
    if (this.context == null) {
        return NO_CONTEXT_MESSAGE;
    }
    try {
        return SHUTDOWN_MESSAGE;
    }
    finally {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500L);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                ShutdownEndpoint.this.context.close();
            }
        });
        thread.setContextClassLoader(getClass().getClassLoader());
        thread.start();
    }
}
```

进一步分析，ShutdownEndpoint 的 invoke 方法调用了 ConfigurableApplicationContext 的 close 方法， ConfigurableApplicationContext 只是一个接口， close 方法的实现类在 AbstractApplicationContext 类。分析到这里，是不是有种熟悉的感觉，不错，AbstractApplicationContext 类就是前面 Spring 添加 JAVA ShutdownHook 的类。AbstractApplicationContext close 方法最终还是调用的 doClose 方法。

```java
public void close() {
    synchronized(this.startupShutdownMonitor) {
        this.doClose();
        if (this.shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
            } catch (IllegalStateException var4) {
            }
        }

    }
}
```

### 2.3 小结

无论测试和源码都说明 2.3 版本之前的 Shutdown 没有优雅停机的功能，基本等同于 执行 ctrl+c 或者 kill -2 或者 -9 。

## 3 Spring Boot（2.3 之前的版本）优雅停机实现

有很多应用使用的是 Spring Boot 2.3 之前的版本，有些使用的还是 1.x 版本。 对于这部分应用我们需要我们自己实现优雅停机的功能。核心思路就是在系统关闭 ShutdownHook 中阻塞 Web 容器的线程池，直到所有请求都处理完毕。不同的 Web 容器有不同的优雅关闭方法。项目已经实现了 Tomcat、Jetty、Undertow 三个 Web 容器的优雅关闭代码，具体代码见：[shutdown-springboot1-graceful](/shutdown-springboot1-graceful/src/main/java)

### 3.1 Tomcat

Tomcat Web 容器关闭代码

```java
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
```

Spring Boot 自动配置代码

```java
@Configuration
@ConditionalOnClass({Servlet.class, Tomcat.class})
public static class TomcatConfiguration {
    @Bean
    public TomcatGracefulShutdown tomcatGracefulShutdown() {
        return new TomcatGracefulShutdown();
    }

    @Bean
    public EmbeddedServletContainerFactory tomcatEmbeddedServletContainerFactory(TomcatGracefulShutdown gracefulShutdown) {
        TomcatEmbeddedServletContainerFactory tomcatFactory = new TomcatEmbeddedServletContainerFactory();
        tomcatFactory.addConnectorCustomizers(gracefulShutdown);
        return tomcatFactory;
    }
}
```

发送[http://localhost:8081/api/hello?serialNo=111&sleepSeconds=10](http://localhost:8081/api/hello?serialNo=111&sleepSeconds=10) 请求进行测试，然后 kill （kill -2（Ctrl + C）、kill -15）应用。测试结果如下：

1. 正在执行操作不会终止，直到执行完成
2. 不再接收新的请求，客户端报错信息为：Connection reset by peer
3. 进程正常终止（业务请求执行完成后，进程立即停止）

### 3.2 Undertow

Undertow Web 容器关闭代码

```java
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
```

Spring Boot 自动配置代码

```java
@Configuration
@ConditionalOnClass({Servlet.class, Undertow.class, SslClientAuthMode.class})
public static class UndertowConfiguration {
    @Bean
    public UndertowGracefulShutdown.UndertowGracefulShutdownWrapper undertowGracefulShutdownWrapper() {
        return new UndertowGracefulShutdown.UndertowGracefulShutdownWrapper();
    }

    @Bean
    public UndertowEmbeddedServletContainerFactory undertowEmbeddedServletContainerFactory(UndertowGracefulShutdown.UndertowGracefulShutdownWrapper wrapper) {
        UndertowEmbeddedServletContainerFactory factory = new UndertowEmbeddedServletContainerFactory();
        factory.addDeploymentInfoCustomizers(deploymentInfo -> deploymentInfo.addOuterHandlerChainWrapper(wrapper));
        return factory;
    }

    @Bean
    public UndertowGracefulShutdown undertowGracefulShutdown(UndertowGracefulShutdown.UndertowGracefulShutdownWrapper wrapper) {
        return new UndertowGracefulShutdown(wrapper);
    }
}
```

发送[http://localhost:8081/api/hello?serialNo=111&sleepSeconds=10](http://localhost:8081/api/hello?serialNo=111&sleepSeconds=10) 请求进行测试，然后 kill （kill -2（Ctrl + C）、kill -15）应用。测试结果如下：

1. 正在执行操作不会终止，直到执行完成
2. 不再接收新的请求，客户端报错信息为：503 Service Unavailable
3. 进程正常终止（业务请求执行完成后20秒进程停止）

### 3.3 Jetty

Jetty Web 容器关闭代码

```java
@Slf4j
public class JettyGracefulShutdown implements ApplicationListener<ContextClosedEvent> {

    private final EmbeddedWebApplicationContext context;

    public JettyGracefulShutdown(EmbeddedWebApplicationContext context) {
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
        EmbeddedServletContainer servletContainer = context.getEmbeddedServletContainer();
        if (servletContainer instanceof JettyEmbeddedServletContainer) {
            log.info("Start to stop jetty servlet container.");
            JettyEmbeddedServletContainer jettyContainer = (JettyEmbeddedServletContainer) servletContainer;
            try {
                jettyContainer.getServer().stop();
            } catch (Exception e) {
                log.warn("Fait to stop jetty thread pool ", e);
            }
        }
    }
}
```

Spring Boot 自动配置代码

```java
@Configuration
@ConditionalOnClass({Servlet.class, Server.class, Loader.class, WebAppContext.class})
public static class JettyConfiguration {
    @Bean
    public JettyEmbeddedServletContainerFactory jettyEmbeddedServletContainerFactory() {
        JettyEmbeddedServletContainerFactory factory = new JettyEmbeddedServletContainerFactory();
        factory.addServerCustomizers(server -> {
            StatisticsHandler handler = new StatisticsHandler();
            handler.setHandler(server.getHandler());
            server.setHandler(handler);
            server.setStopTimeout(20 * 1000);
            server.setStopAtShutdown(false);
        });
        return factory;
    }

    @Bean
    public JettyGracefulShutdown jettyGracefulShutdown(EmbeddedWebApplicationContext context) {
        return new JettyGracefulShutdown(context);
    }
}
```

发送[http://localhost:8081/api/hello?serialNo=111&sleepSeconds=10](http://localhost:8081/api/hello?serialNo=111&sleepSeconds=10) 请求进行测试，然后 kill （kill -2（Ctrl + C）、kill -15）应用。测试结果如下：

1. 正在执行操作不会终止，直到执行完成
2. 不再接收新的请求，客户端报错信息为：Connection refused
3. 进程正常终止（ kill 命令发出后20秒进程停止）

## 4 Spring Boot（2.3 之后的版本）优雅停机

在最新的 SpringBoot 2.3.0 版本中，正式内置了优雅停机功能，不需要再自行扩展线程池来处理。

当启动server.shutdown=graceful，在应用关闭时，Web 服务器将不再接受新请求，并等待正在进行的请求完成的缓冲时间。配置如下：

```text
# 开启优雅停机，默认值：immediate 为立即关闭
server.shutdown=graceful

# 设置缓冲期，最大等待时间，默认：30秒
spring.lifecycle.timeout-per-shutdown-phase=60s
```

## 5 多负载下的优雅启停

前面介绍的 Spring Boot 应用优雅停机都是针对单机，只是保证了服务器内部请求执行完毕，无法完成新请求的响应。在生产环境，我们的服务会有多台负载，服务的前面会有网关或者负载均衡之类的组件。只要我们能够做到：

1. 优雅停机。在应用关闭前，通知网关让服务下线，这样就不会有新请求过来，再配合优雅停机处理完正在进行的请求。
2. 优雅启动。在应用启动后，直到应用启动完成并且健康检查通过后，才注册服务到网关，接收请求。

### 5.1 应用状态

设计了4种应用状态，RUNNING（正常服务）、BROKEN（应用不能正常服务）、SHUTDOWNING（关闭中）、STARTING（启动中）。 

关闭应用前，将应用状态设置为 SHUTDOWNING（关闭中），这时应用状态检查接口返回 503（服务不可达）错误，一定周期后，网关会检测到服务不可用，将该节点下线。然后再执行真正的应用关闭命令。

启动应用后，应用状态初始值为 STARTING（启动中），这时应用状态检查接口返回 503（服务不可达）错误。直到应用健康状态检查通过后，才会将应用状态设置为 RUNNING（正常服务），这时应用状态检查接口返回 200，经过一定周期后，网关检测到服务可用，将该节点上线。

源码见[shutdown-springboot1-graceful-state](/shutdown-springboot1-graceful-state)

应用状态扩展点 [AppStateEndpoint](/shutdown-springboot1-graceful-state/src/main/java/com/jinpei/graceful/shutdown/springboot1/state/AppStateEndpoint.java)

```java
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
```

应用状态MVC扩展点 [AppStateMvcEndpoint](/shutdown-springboot1-graceful-state/src/main/java/com/jinpei/graceful/shutdown/springboot1/state/AppStateMvcEndpoint.java)

```java
@ConfigurationProperties(prefix = "endpoints.appstate")
public class AppStateMvcEndpoint extends EndpointMvcAdapter {
    private final AppStateEndpoint appStateEndpoint;

    public AppStateMvcEndpoint(AppStateEndpoint delegate) {
        super(delegate);
        this.appStateEndpoint = delegate;
    }

    @RequestMapping(value = "/shutdown", method = RequestMethod.POST, produces = {ActuatorMediaTypes.APPLICATION_ACTUATOR_V1_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public Map<String, Object> shutdown() {
        return appStateEndpoint.shutdown();
    }

    @RequestMapping(value = "/ready", method = RequestMethod.POST, produces = {ActuatorMediaTypes.APPLICATION_ACTUATOR_V1_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public Map<String, Object> ready() {
        return appStateEndpoint.ready();
    }
}
```

应用状态健康检查接口，项目是根据 Nginx 反向代理设计的，Nginx upstream 健康检查需要使用业务端口，默认Spring Boot 健康检查使用管理端口。逻辑代码是相同的，可以根据实际情况使用。[AppStateHealthIndicator](/shutdown-springboot1-graceful-state/src/main/java/com/jinpei/graceful/shutdown/springboot1/state/AppStateHealthIndicator.java)

```java
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
```

自动配置类 [AppStateAutoConfiguration](/shutdown-springboot1-graceful-state/src/main/java/com/jinpei/graceful/shutdown/springboot1/autoconfigure/AppStateAutoConfiguration.java)

```java
@Configuration
@EnableConfigurationProperties(AppStateCheckProperties.class)
public class AppStateAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "endpoints.appstate.check", value = "enable", havingValue = "true", matchIfMissing = true)
    public AppStateHealthIndicator AppStateHealthIndicator() {
        return new AppStateHealthIndicator();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnEnabledEndpoint(value = "appstate")
    public AppStateEndpoint appStateEndpoint() {
        return new AppStateEndpoint();
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnClass({EndpointMvcAdapter.class})
    @ConditionalOnBean(AppStateEndpoint.class)
    @ConditionalOnMissingBean
    public AppStateMvcEndpoint appStateMvcEndpoint(AppStateEndpoint appStateEndpoint) {
        return new AppStateMvcEndpoint(appStateEndpoint);
    }
}
```

## 6 参考

参考了如下文章或代码:

[https://github.com/spring-projects/spring-boot/issues/4657](https://github.com/spring-projects/spring-boot/issues/4657)

[https://github.com/jihor/hiatus-spring-boot](https://github.com/jihor/hiatus-spring-boot)
