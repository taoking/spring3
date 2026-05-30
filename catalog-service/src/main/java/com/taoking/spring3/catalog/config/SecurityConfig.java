package com.taoking.spring3.catalog.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfig.JwtProperties.class)
class SecurityConfig {

    private static final String DEFAULT_JWT_SECRET = "spring3-local-dev-secret-key-32-bytes-minimum";

    @Bean
    @ConditionalOnProperty(prefix = "demo.security", name = "mode", havingValue = "basic", matchIfMissing = true)
    SecurityFilterChain basicSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(withDefaults())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "demo.security", name = "mode", havingValue = "jwt")
    SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .httpBasic(withDefaults())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "demo.security", name = "mode", havingValue = "jwt")
    JwtDecoder jwtDecoder(JwtProperties properties) {
        if (StringUtils.hasText(properties.jwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        }
        if (StringUtils.hasText(properties.issuerUri())) {
            return JwtDecoders.fromIssuerLocation(properties.issuerUri());
        }
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(secret.length >= 32, "demo.security.jwt.secret must be at least 32 bytes for HS256");
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "demo.security", name = "mode", havingValue = "jwt")
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> grantedAuthorities(jwt, scopeConverter));
        return converter;
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("user")
                        .password(passwordEncoder.encode("user123"))
                        .roles("USER")
                        .build(),
                User.withUsername("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .roles("USER", "ADMIN")
                        .build()
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private Collection<GrantedAuthority> grantedAuthorities(Jwt jwt, JwtGrantedAuthoritiesConverter scopeConverter) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
        if (scopeAuthorities != null) {
            authorities.addAll(scopeAuthorities);
        }
        claimValues(jwt, "roles").stream()
                .filter(StringUtils::hasText)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    private List<String> claimValues(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .toList();
        }
        if (claim instanceof String value && StringUtils.hasText(value)) {
            return Arrays.stream(value.split("[,\\s]+"))
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    @ConfigurationProperties(prefix = "demo.security.jwt")
    record JwtProperties(String secret, String issuerUri, String jwkSetUri) {

        JwtProperties {
            secret = StringUtils.hasText(secret) ? secret : DEFAULT_JWT_SECRET;
        }
    }
}
