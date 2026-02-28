package com.kailas.settlementengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_USER_PASSWORD = "user123";

    @Value("${APP_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${APP_ADMIN_PASSWORD:" + DEFAULT_ADMIN_PASSWORD + "}")
    private String adminPassword;

    @Value("${APP_USER_USERNAME:user}")
    private String userUsername;

    @Value("${APP_USER_PASSWORD:" + DEFAULT_USER_PASSWORD + "}")
    private String userPassword;

    @Value("${RAILWAY_ENVIRONMENT:}")
    private String railwayEnvironment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/login.html",
                                "/error",
                                "/favicon.ico",
                                "/style.css",
                                "/app.js",
                                "/login.css"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/customers",
                                "/merchants",
                                "/transactions",
                                "/logs",
                                "/api/settlements/stats",
                                "/api/reconciliation/exceptions",
                                "/api/auth/me"
                        ).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/customers",
                                "/merchants",
                                "/transactions",
                                "/settlement/trigger",
                                "/api/reconciliation/run",
                                "/api/reconciliation/exceptions/*/retry",
                                "/api/reconciliation/exceptions/*/resolve"
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .failureUrl("/login.html?error")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login.html?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        validateCredentialConfiguration();

        var admin = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "USER")
                .build();

        var user = User.withUsername(userUsername)
                .password(passwordEncoder.encode(userPassword))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void validateCredentialConfiguration() {
        boolean adminPasswordUsingFallback =
                System.getenv("APP_ADMIN_PASSWORD") == null
                        && DEFAULT_ADMIN_PASSWORD.equals(adminPassword);
        boolean userPasswordUsingFallback =
                System.getenv("APP_USER_PASSWORD") == null
                        && DEFAULT_USER_PASSWORD.equals(userPassword);

        boolean usingDefaultCredentials = adminPasswordUsingFallback || userPasswordUsingFallback;

        if (usingDefaultCredentials && railwayEnvironment != null && !railwayEnvironment.isBlank()) {
            throw new IllegalStateException(
                    "Default security passwords are not allowed on Railway. "
                            + "Set APP_ADMIN_PASSWORD and APP_USER_PASSWORD environment variables."
            );
        }

        if (usingDefaultCredentials) {
            log.warn("Running with default security passwords. Set APP_ADMIN_PASSWORD and APP_USER_PASSWORD.");
        }
    }
}
