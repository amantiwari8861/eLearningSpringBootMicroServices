package in.iamandroid.order.config;

import in.iamandroid.order.security.ApiErrorResponseWriter;
import in.iamandroid.order.security.IdentityHeaderAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final IdentityHeaderAuthenticationFilter identityHeaderFilter;
    private final ApiErrorResponseWriter errorResponseWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(identityHeaderFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/orders/enrollments",
                                "/api/v1/orders/lessons/*/progress"
                        ).hasAnyRole("TRAINEE", "ADMIN")
                        .requestMatchers("/api/v1/orders/**").authenticated()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"lms\"");
                            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                                    "UNAUTHENTICATED", "Authentication is required to access this resource.");
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "You do not have permission to perform this action.")));

        return http.build();
    }
}
