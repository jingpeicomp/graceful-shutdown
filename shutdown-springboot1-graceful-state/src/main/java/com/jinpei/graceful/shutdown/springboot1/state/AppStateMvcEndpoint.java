package com.jinpei.graceful.shutdown.springboot1.state;

import org.springframework.boot.actuate.endpoint.mvc.ActuatorMediaTypes;
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 应用状态MVC扩展点
 * Created by liuzhaoming on 2020/12/23
 */
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
