package it.buonacaccia;

import org.springframework.boot.SpringApplication;

/**
 * Avvio locale con i mock: la stessa applicazione, ma con il classpath dei test, dove stanno
 * gli utenti di prova e l'emittente dei token (package {@code mock}). Usa il MongoDB di
 * compose.yaml e le variabili di test-src/env/mocked.env (o test.env).
 * Da IntelliJ: Run su questa classe; da riga di comando: {@code ./gradlew bootTestRun}.
 */
public class TestBuonaCacciaApplication {

    public static void main(String[] args) {
        SpringApplication.from(BuonaCacciaApplication::main).run(args);
    }
}
