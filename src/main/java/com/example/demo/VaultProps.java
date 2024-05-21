package com.example.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.vault")
@Data
public class VaultProps {
    private String host;
    private int port;
    private String scheme;
    private String authentication;
    private String token;
}