package br.com.camplana;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DotEnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        try {
            Map<String, Object> envProperties = new HashMap<>();
            Files.lines(Paths.get(".env")).forEach(line -> {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                    int index = line.indexOf("=");
                    String key = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();
                    envProperties.put(key, value);
                }
            });

            if (!envProperties.isEmpty()) {
                context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("dotEnvProperties", envProperties));
            }
        } catch (IOException e) {
            System.out.println("Arquivo .env não localizado. Usando variáveis do sistema.");
        }
    }
}
