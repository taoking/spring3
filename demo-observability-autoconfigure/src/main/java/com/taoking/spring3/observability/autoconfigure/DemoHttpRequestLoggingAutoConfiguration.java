package com.taoking.spring3.observability.autoconfigure;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class})
@ConditionalOnProperty(prefix = "demo.observability.http-logging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DemoHttpRequestLoggingProperties.class)
public class DemoHttpRequestLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DemoHttpRequestLoggingFilter demoHttpRequestLoggingFilter(DemoHttpRequestLoggingProperties properties) {
        return new DemoHttpRequestLoggingFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "demoHttpRequestLoggingFilterRegistration")
    FilterRegistrationBean<DemoHttpRequestLoggingFilter> demoHttpRequestLoggingFilterRegistration(
            DemoHttpRequestLoggingFilter filter
    ) {
        FilterRegistrationBean<DemoHttpRequestLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("demoHttpRequestLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
