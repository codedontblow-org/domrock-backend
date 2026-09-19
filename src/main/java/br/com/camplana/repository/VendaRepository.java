package br.com.camplana.repository;

import br.com.camplana.entity.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface VendaRepository extends JpaRepository<Venda, Integer> {

    boolean existsByDateRefBetween(LocalDate inicio, LocalDate fim);

}