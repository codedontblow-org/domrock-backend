package br.com.camplana.repository;

import br.com.camplana.entity.FuncionarioLoja;
import br.com.camplana.entity.FuncionarioLojaId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface FuncionarioLojaRepository extends JpaRepository<FuncionarioLoja, FuncionarioLojaId> {

    boolean existsByIdDateRef(LocalDate dateRef);

    boolean existsByIdFuncionarioIdAndIdDateRef(Integer funcionarioId, LocalDate dateRef);

}