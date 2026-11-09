package io.github.jkanie.schedkeel.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserProperties.class)
class SecurityConfiguration {

    static final String ADMIN = "ADMIN";
    static final String VIEWER = "VIEWER";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole(VIEWER, ADMIN)
                        .requestMatchers("/api/**")
                        .hasRole(ADMIN)
                        .anyRequest()
                        .permitAll())
                .formLogin(form -> form.loginProcessingUrl("/api/login")
                        .successHandler(
                                (request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler(
                                (request, response, e) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(logout -> logout.logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // An API has no page to return to after sign-in, so a 401 must not
                // store the request (and open a Redis session) for every caller.
                .requestCache(cache -> cache.disable())
                // The dashboard reads the XSRF-TOKEN cookie and echoes it in a header.
                // Requests that carry their own credentials (the CLI, scripts) are not
                // open to cross-site forgery, so they skip the check.
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(request -> request.getHeader("Authorization") != null))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UserProperties props, PasswordEncoder encoder) {
        var manager = new InMemoryUserDetailsManager();
        for (UserProperties.User user : props.users()) {
            if (user.password() == null || user.password().isBlank()) {
                throw new IllegalStateException("User '" + user.username()
                        + "' has no password. Set it through the environment; see README.md.");
            }
            manager.createUser(User.withUsername(user.username())
                    .password(encoder.encode(user.password()))
                    .roles(user.roles().toArray(String[]::new))
                    .build());
        }
        return manager;
    }
}
