package it.buonacaccia.config.guard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Guardia di un endpoint (research R4), sul modello di ScoutShopper-be. Il token deve contenere
 * almeno uno dei ruoli in {@link #role()} e almeno uno degli entitlement in {@link #entitlement()};
 * un attributo vuoto non pone vincoli, quindi {@code @SecurityAccess} da sola chiede solo un token valido.
 * Le operazioni si proteggono con l'entitlement (AUTH-1); il ruolo serve per le aree di un ruolo.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityAccess {

    String[] role() default {};

    String[] entitlement() default {};
}
