package br.com.camplana;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class DotEnvInitializerTests {

	@Test
	void doesNotFailWhenEnvFileIsMissingOrPresent() {
		try (GenericApplicationContext context = new GenericApplicationContext()) {
			new DotEnvInitializer().initialize(context);

			assertThat(context.getEnvironment()).isNotNull();
		}
	}

}
