package br.com.camplana.repository;

import br.com.camplana.entity.FuncionarioCargo;
import br.com.camplana.entity.FuncionarioCargoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncionarioCargoRepository extends JpaRepository<FuncionarioCargo, FuncionarioCargoId> {
}