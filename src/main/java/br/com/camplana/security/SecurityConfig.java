package br.com.camplana.security;

import br.com.camplana.handler.SecurityErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final UsuarioDetailsService usuarioDetailsService;
    private final SecurityErrorHandler securityErrorHandler;
    private  final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {

                    // QUALQUER UM -> TENTATIVA DE LOGIN
                    req.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();

                    req.requestMatchers("/error").permitAll();

                    // GERENTE e SUPERVISOR -> CHAT E SIMULACOES
                    req.requestMatchers("/api/chat/**").hasAnyRole("GERENTE", "SUPERVISOR");
                    req.requestMatchers("/api/simulacoes/**").hasAnyRole("GERENTE", "SUPERVISOR");

                    // SUPERVISOR -> DECIDE CAMPANHA
                    req.requestMatchers(HttpMethod.POST, "/api/campanhas/*/decidir").hasRole("SUPERVISOR");

                    // GERENTE e SUPERVISOR -> CRIA CAMPANHA
                    req.requestMatchers("/api/campanhas/**").hasAnyRole("GERENTE", "SUPERVISOR");

                    //ADMIN -> CONTROLE SOBRE USUARIOS
                    req.requestMatchers("/api/usuarios/**").hasRole("ADMIN");

                    //SUPERVISOR -> PODE IMPORTAR DADOS
                    req.requestMatchers("/api/importacao/**").hasRole("SUPERVISOR");

                    req.anyRequest().denyAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }


    // CONFERE LOGIN ( EMAIL E SENHA ) E BUSCA BANCO
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // IMPEDE O SPRING BOOT DE REGISTRAR O SecurityFilter TAMBÉM NO TOMCAT
    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration(SecurityFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
