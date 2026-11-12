package com.eclectics.worksync.notifications.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("email")
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
}
