package com.jinpei.graceful.shutdown.springboot1;

/**
 * Java系统关闭demo
 * Created by liuzhaoming on 2020/12/24
 */
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
