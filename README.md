# 优雅停机

## 单应用优雅停机的定义

优雅停机是指关闭应用程序时，在规定的超时时间范围内，允许进行中的请求完成，拒绝新的请求进入。这将使应用在请求处理方面保持一致，即没有未处理请求，每一个请求都被处理（完成或拒绝）。优雅停机包含三个要素：

1. 如何关闭应用程序
2. 完成正在进行中的请求
3. 新的请求将被拒绝

### 应用关闭命令-Linux Kill命令

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

### JAVA 对于应用关闭的底层支持

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

1. ShutdownHook 的调用是不保证顺序的
2. ShutdownHook 是JVM结束前调用的线程，所以该线程中的方法应尽量短，并且保证不能发生死锁的情况，否则也会阻止JVM的正常退出
3. ShutdownHook 中不能执行 System.exit()，否则会导致虚拟机卡住，而不得不强行杀死进程

### Spring 添加 ShutdownHook

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

## Spring Boot Web 应用优雅停机

在 Spring Boot 2.3 之前版本是没有优雅停机的功能，见：[https://github.com/spring-projects/spring-boot/issues/4657](https://github.com/spring-projects/spring-boot/issues/4657)。Spring Boot Actuator 提供的 Shutdown 并不能实现优雅停机。 测试代码见：[shutdown-springboot1](/shutdown-springboot1)