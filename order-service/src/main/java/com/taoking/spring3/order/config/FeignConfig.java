package com.taoking.spring3.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FeignConfig {

    @Bean
    RequestInterceptor catalogBasicAuthInterceptor(CatalogClientProperties properties) {
        String token = Base64.getEncoder().encodeToString(
                (properties.username() + ":" + properties.password()).getBytes(StandardCharsets.UTF_8)
        );
        return requestTemplate -> requestTemplate.header("Authorization", "Basic " + token);
    }

    @Bean
    RequestInterceptor tracingRequestInterceptor(
            ObjectProvider<Tracer> tracerProvider,
            ObjectProvider<Propagator> propagatorProvider
    ) {
        Propagator.Setter<RequestTemplate> setter = RequestTemplate::header;
        return requestTemplate -> {
            Tracer tracer = tracerProvider.getIfAvailable();
            Propagator propagator = propagatorProvider.getIfAvailable();
            if (tracer == null || propagator == null) {
                return;
            }
            TraceContext context = tracer.currentTraceContext().context();
            if (context != null) {
                propagator.inject(context, requestTemplate, setter);
            }
        };
    }
}
