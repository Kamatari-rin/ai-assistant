package com.example.aiassistant.orders.config;

import com.example.aiassistant.orders.domain.EscalationEligibilityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class EscalationConfiguration {

    @Bean
    public EscalationEligibilityPolicy escalationEligibilityPolicy() {

        return new EscalationEligibilityPolicy();
    }

    @Bean
    public Clock escalationClock() {

        return Clock.systemUTC();
    }
}