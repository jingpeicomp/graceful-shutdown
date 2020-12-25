package com.jinpei.graceful.shutdown.springboot1.autoconfigure;

import com.jinpei.graceful.shutdown.springboot1.state.AppStateCheckProperties;
import com.jinpei.graceful.shutdown.springboot1.state.AppStateEndpoint;
import com.jinpei.graceful.shutdown.springboot1.state.AppStateHealthIndicator;
import com.jinpei.graceful.shutdown.springboot1.state.AppStateMvcEndpoint;
import org.springframework.boot.actuate.condition.ConditionalOnEnabledEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用状态自动配置
 * Created by liuzhaoming on 2020/12/23
 */
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
