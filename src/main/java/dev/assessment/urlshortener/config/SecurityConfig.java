package dev.assessment.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/links/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/links/*/analytics").hasRole("ADMIN")
                        .requestMatchers("/r/**", "/api/v1/links/**", "/actuator/health/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/v1/engineering/**").authenticated()
                        .requestMatchers("/api/v1/orchestration/runs/*/approvals").hasAnyRole("APPROVER", "ADMIN")
                        .requestMatchers("/api/v1/orchestration/**").authenticated()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${app.security.submitter-password:local-submitter-only}") String submitterPassword,
            @Value("${app.security.approver-password:local-approver-only}") String approverPassword,
            @Value("${app.security.admin-password:local-admin-only}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("submitter").password(encoder.encode(submitterPassword)).roles("SUBMITTER").build(),
                User.withUsername("approver").password(encoder.encode(approverPassword)).roles("APPROVER").build(),
                User.withUsername("admin").password(encoder.encode(adminPassword)).roles("ADMIN").build());
    }
}
