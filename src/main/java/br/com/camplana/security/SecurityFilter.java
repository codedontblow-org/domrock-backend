package br.com.camplana.security;

import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private static final String HEADER_DEV = "X-Usuario-Dev";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final Environment env;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Optional<Usuario> usuario = identificarUsuario(request);

        usuario.filter(Usuario::isEnabled).ifPresent(u -> {
            var authentication = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });

        filterChain.doFilter(request, response);
    }

    private Optional<Usuario> identificarUsuario(HttpServletRequest request) {
        if (env.acceptsProfiles(Profiles.of("dev"))) {
            String devHeader = request.getHeader(HEADER_DEV);
            if (devHeader != null && !devHeader.isBlank()) {
                return buscarPorIdDev(devHeader);
            }
        }
        return buscarPorToken(request);
    }

    private Optional<Usuario> buscarPorIdDev(String devHeader) {
        try {
            return usuarioRepository.findById(Integer.parseInt(devHeader.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Usuario> buscarPorToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            String email = jwtService.getSubject(authorization.substring(7));
            return usuarioRepository.findByEmail(email);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
