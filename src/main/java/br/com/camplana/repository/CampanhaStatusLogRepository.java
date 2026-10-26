package br.com.camplana.repository;

import br.com.camplana.entity.CampanhaStatusLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampanhaStatusLogRepository extends JpaRepository<CampanhaStatusLog, Long> {

    // id desempata linhas gravadas no mesmo instante (ex.: relógio fixo nos testes)
    @EntityGraph(attributePaths = {"usuario", "versao"})
    List<CampanhaStatusLog> findByCampanhaIdOrderByEmAscIdAsc(Long campanhaId);

    Optional<CampanhaStatusLog> findFirstByVersaoIdOrderByEmDescIdDesc(Long versaoId);

    /** Existe outra campanha aprovada cujo período cruza com o desta versão, DEPOIS da submissão? */
    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END
            FROM CampanhaStatusLog l
            WHERE l.campanha.id <> :campanhaId
              AND l.versao IS NOT NULL
              AND l.para = br.com.camplana.entity.StatusCampanha.APROVADA
              AND l.em > :submetidaEm
              AND l.versao.dataInicio <= :dataFim
              AND l.versao.dataFim >= :dataInicio
            """)
    boolean existeAprovacaoConflitanteDepoisDe(
            @Param("campanhaId") Long campanhaId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            @Param("submetidaEm") Instant submetidaEm);
}