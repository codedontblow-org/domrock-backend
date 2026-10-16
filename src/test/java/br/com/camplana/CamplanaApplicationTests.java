package br.com.camplana;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres",
		"spring.flyway.placeholders.lana_db_password=lana_leitura",
		"lana.url=http://localhost:8000"
})
class CamplanaApplicationTests {

	@Test
	void contextLoads() {
	}

}
