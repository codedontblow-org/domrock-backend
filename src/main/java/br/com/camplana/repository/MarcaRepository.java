package br.com.camplana.repository;

import br.com.camplana.entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {
    Optional<Marca> findByCodMarca(String codMarca);
}