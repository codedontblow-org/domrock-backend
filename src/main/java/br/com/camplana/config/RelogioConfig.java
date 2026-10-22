package br.com.camplana.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * "Agora" injetável, para os testes fixarem a data. O fuso só pesa nas datas do negócio (vigência
 * da campanha); os horários gravados são Instant, em UTC.
 */
@Configuration
public class RelogioConfig {

    // Em milissegundos: o JDK dá nanossegundos e o Postgres guarda microssegundos, então a resposta
    // do salvar e a leitura seguinte mostrariam horas diferentes para o mesmo registro.
    @Bean
    public Clock relogio() {
        return Clock.tick(Clock.system(ZoneId.of("America/Sao_Paulo")), Duration.ofMillis(1));
    }
}
