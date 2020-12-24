package com.jinpei.graceful.shutdown.springboot1.autoconfigure;

import com.jinpei.graceful.shutdown.springboot1.handler.JettyGracefulShutdown;
import com.jinpei.graceful.shutdown.springboot1.handler.TomcatGracefulShutdown;
import com.jinpei.graceful.shutdown.springboot1.handler.UndertowGracefulShutdown;
import io.undertow.Undertow;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.startup.Tomcat;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xnio.SslClientAuthMode;

import javax.servlet.Servlet;

/**
 * Created by liuzhaoming on 2020/12/21
 */
@Configuration
@Slf4j
public class ShutdownAutoConfiguration {
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

    @Configuration
    @ConditionalOnClass({Servlet.class, Undertow.class, SslClientAuthMode.class})
    public static class UndertowConfiguration {
//        @Bean
        public UndertowGracefulShutdown.UndertowGracefulShutdownWrapper undertowGracefulShutdownWrapper() {
            return new UndertowGracefulShutdown.UndertowGracefulShutdownWrapper();
        }

//        @Bean
        public UndertowEmbeddedServletContainerFactory undertowEmbeddedServletContainerFactory(UndertowGracefulShutdown.UndertowGracefulShutdownWrapper wrapper) {
            UndertowEmbeddedServletContainerFactory factory = new UndertowEmbeddedServletContainerFactory();
            factory.addDeploymentInfoCustomizers(deploymentInfo -> deploymentInfo.addOuterHandlerChainWrapper(wrapper));
            return factory;
        }

//        @Bean
        public UndertowGracefulShutdown undertowGracefulShutdown(UndertowGracefulShutdown.UndertowGracefulShutdownWrapper wrapper) {
            return new UndertowGracefulShutdown(wrapper);
        }
    }

    @Configuration
    @ConditionalOnClass({Servlet.class, Server.class, Loader.class, WebAppContext.class})
    public static class JettyConfiguration {
//        @Bean
        public JettyEmbeddedServletContainerFactory jettyEmbeddedServletContainerFactory() {
            return new JettyEmbeddedServletContainerFactory();
        }

//        @Bean
        public JettyGracefulShutdown jettyGracefulShutdown(EmbeddedWebApplicationContext context) {
            return new JettyGracefulShutdown(context);
        }
    }
}
