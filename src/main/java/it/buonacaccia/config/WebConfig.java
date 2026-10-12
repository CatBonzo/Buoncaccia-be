package it.buonacaccia.config;

import it.buonacaccia.config.guard.SecurityGuardInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la guardia {@code @SecurityAccess} su tutte le richieste. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final SecurityGuardInterceptor securityGuardInterceptor;

    public WebConfig(SecurityGuardInterceptor securityGuardInterceptor) {
        this.securityGuardInterceptor = securityGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityGuardInterceptor);
    }
}
