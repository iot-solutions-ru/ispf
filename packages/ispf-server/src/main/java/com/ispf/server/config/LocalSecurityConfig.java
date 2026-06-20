package com.ispf.server.config;

import com.ispf.server.security.PlatformUserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@Profile({"local", "test"})
@EnableConfigurationProperties(IspfSecurityProperties.class)
public class LocalSecurityConfig {

    @Bean
    SecurityFilterChain localSecurityFilterChain(
            HttpSecurity http,
            IspfSecurityProperties properties,
            PlatformUserService userService
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (properties.isRbacEnabled()) {
            if (properties.isTokenAuthEnabled()) {
                http.addFilterBefore(
                        new LocalBearerTokenFilter(userService),
                        UsernamePasswordAuthenticationFilter.class
                );
            }
            http.addFilterBefore(
                    new LocalRoleHeaderFilter(properties),
                    UsernamePasswordAuthenticationFilter.class
            );
            http.authorizeHttpRequests(IspfAuthorizationRules::apply);
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
