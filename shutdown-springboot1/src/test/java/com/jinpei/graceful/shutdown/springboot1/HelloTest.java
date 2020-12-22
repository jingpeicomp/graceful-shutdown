package com.jinpei.graceful.shutdown.springboot1;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Created by liuzhaoming on 2020/12/21
 */
@Slf4j
public class HelloTest {
    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    public void hello() throws InterruptedException {
        ResponseEntity<String> responseEntity = null;
        try {
            log.info("Begin request serialNo {}", 1);
            String url = "http://localhost:8081/api/hello?serialNo=%d&sleepSeconds=10";
            responseEntity = restTemplate.getForEntity(String.format(url, 1), String.class);
            log.info("Response http code is {} , and content is {}", responseEntity.getStatusCodeValue(), responseEntity.getBody());
        } catch (Exception e) {
            log.error("Fait to request serialNo {} {} {}", 1, null != responseEntity ? responseEntity.getStatusCodeValue() : null, e.getMessage());
            Thread.sleep(1000);
        }
    }
}