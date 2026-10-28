package br.com.camplana.repository;

import br.com.camplana.entity.FuncionarioCargo;
import br.com.camplana.entity.FuncionarioCargoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FuncionarioCargoRepository extends JpaRepository<FuncionarioCargo, FuncionarioCargoId> {

    boolean existsByIdFuncionarioIdAndIdCargoIdAndIdDateRef(Integer funcionarioId, Integer cargoId, LocalDate dateRef);

    /** A alocação de cargo EXATA que o "antes" descreve (a PK inclui cargo_id). */
    @Query("""
            select fc from FuncionarioCargo fc
            join fetch fc.cargo c
            where fc.id.funcionarioId = :funcionarioId
              and fc.id.dateRef = :dateRef
              and c.codCargo = :codCargo
            """)
    Optional<FuncionarioCargo> findAlocacaoPorCodCargo(@Param("funcionarioId") Integer funcionarioId,
                                                       @Param("dateRef") LocalDate dateRef,
                                                       @Param("codCargo") String codCargo);

    /** Todos os cargos do mês, para informar o valor atual na mensagem de erro. */
    @Query("""
            select fc from FuncionarioCargo fc
            join fetch fc.cargo c
            where fc.id.funcionarioId = :funcionarioId
              and fc.id.dateRef = :dateRef
            order by c.codCargo
            """)
    List<FuncionarioCargo> findAlocacoesDaCompetencia(@Param("funcionarioId") Integer funcionarioId,
                                                      @Param("dateRef") LocalDate dateRef);
}
