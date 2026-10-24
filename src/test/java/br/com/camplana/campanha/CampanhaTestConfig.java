package br.com.camplana.campanha;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Troca o registro de simulações pelo fake e fixa "agora" em 21/10/2026 13:05 UTC. */
@TestConfiguration(proxyBeanMethods = false)
public class CampanhaTestConfig {

    public static final Instant AGORA = Instant.parse("2026-10-21T13:05:00Z");

    @Bean
    @Primary
    FakeRegistroSimulacoes fakeRegistroSimulacoes() {
        return new FakeRegistroSimulacoes();
    }

    @Bean
    @Primary
    Clock relogioFixo() {
        return Clock.fixed(AGORA, ZoneId.of("America/Sao_Paulo"));
    }
}
