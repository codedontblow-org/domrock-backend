package br.com.camplana.repository;

import br.com.camplana.entity.FuncionarioLoja;
import br.com.camplana.entity.FuncionarioLojaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FuncionarioLojaRepository extends JpaRepository<FuncionarioLoja, FuncionarioLojaId> {

    boolean existsByIdDateRef(LocalDate dateRef);

    boolean existsByIdFuncionarioIdAndIdDateRef(Integer funcionarioId, LocalDate dateRef);

    /** Serve para detectar colisão: o funcionário já está nessa loja nessa competência? */
    boolean existsByIdFuncionarioIdAndIdLojaIdAndIdDateRef(Integer funcionarioId, Integer lojaId, LocalDate dateRef);

    /**
     * A alocação EXATA que o "antes" da correção descreve. A PK inclui loja_id, então o funcionário
     * pode ter mais de uma linha no mês (cobertura): identificar pelo código evita ambiguidade.
     */
    @Query("""
            select fl from FuncionarioLoja fl
            join fetch fl.loja l
            where fl.id.funcionarioId = :funcionarioId
              and fl.id.dateRef = :dateRef
              and l.codLoja = :codLoja
            """)
    Optional<FuncionarioLoja> findAlocacaoPorCodLoja(@Param("funcionarioId") Integer funcionarioId,
                                                     @Param("dateRef") LocalDate dateRef,
                                                     @Param("codLoja") String codLoja);

    /** Todas as alocações do mês, para informar o valor atual na mensagem de erro. */
    @Query("""
            select fl from FuncionarioLoja fl
            join fetch fl.loja l
            where fl.id.funcionarioId = :funcionarioId
              and fl.id.dateRef = :dateRef
            order by l.codLoja
            """)
    List<FuncionarioLoja> findAlocacoesDaCompetencia(@Param("funcionarioId") Integer funcionarioId,
                                                     @Param("dateRef") LocalDate dateRef);
}
