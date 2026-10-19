package br.com.camplana;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class CamplanaApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(CamplanaApplication.class).run(args);
	}
}
