package br.com.camplana.repository;

import br.com.camplana.entity.Loja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LojaRepository extends JpaRepository<Loja, Integer> {
    Optional<Loja> findByCodLoja(String codLoja);
}