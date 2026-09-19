package br.com.camplana.repository;

import br.com.camplana.entity.Cargo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CargoRepository extends JpaRepository<Cargo, Integer> {
    Optional<Cargo> findByCodCargo(String codCargo);
}