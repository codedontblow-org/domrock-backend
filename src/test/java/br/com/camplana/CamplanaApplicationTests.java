package br.com.camplana;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres",
        "spring.flyway.placeholders.lana_db_password=lana_leitura",
        "lana.url=http://localhost:8000",
        "grpc.lana.host=localhost",
        "grpc.lana.port=50051"
})
@ActiveProfiles("test")
class CamplanaApplicationTests {

	@Test
	void contextLoads() {
	}

}
