package br.com.camplana;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseConnectionTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Environment environment;

	@Test
	void dataSourceProvidesValidConnection() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
		}
	}

	@Test
	void canExecuteSimpleQuery() {
		Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

		assertThat(result).isEqualTo(1);
	}

	@Test
	void jpaEntityManagerIsAvailable() {
		assertThat(entityManager.isOpen()).isTrue();
		Object result = entityManager.createNativeQuery("SELECT 1").getSingleResult();

		assertThat(result).isEqualTo(1);
	}

	@Test
	void applicationContextIsLoaded() {
		assertThat(context).isNotNull();
		assertThat(environment.getProperty("spring.application.name")).isEqualTo("camplana");
	}

}
