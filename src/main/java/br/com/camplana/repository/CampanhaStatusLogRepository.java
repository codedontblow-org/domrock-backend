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

@Repository
public interface CampanhaStatusLogRepository extends JpaRepository<CampanhaStatusLog, Long> {

    // id desempata linhas gravadas no mesmo instante (ex.: relógio fixo nos testes)
    @EntityGraph(attributePaths = {"usuario", "versao"})
    List<CampanhaStatusLog> findByCampanhaIdOrderByEmAscIdAsc(Long campanhaId);

    /**
     * Aprovações de outras campanhas cujo período cruza [inicio, fim], gravadas depois de {@code depoisDe}
     * (contrato 5.6, {@code aprovadas_depois}). Da mais antiga para a mais nova.
     */
    @Query("""
            SELECT l FROM CampanhaStatusLog l JOIN FETCH l.versao v JOIN FETCH v.campanha c JOIN FETCH l.usuario
            WHERE l.para = br.com.camplana.entity.StatusCampanha.APROVADA AND c.id <> :campanhaId
              AND l.em > :depoisDe AND v.dataInicio <= :fim AND v.dataFim >= :inicio
            ORDER BY l.em ASC, l.id ASC
            """)
    List<CampanhaStatusLog> findAprovacoesQueCruzamDepoisDe(@Param("campanhaId") Long campanhaId,
                                                           @Param("depoisDe") Instant depoisDe,
                                                           @Param("inicio") LocalDate inicio,
                                                           @Param("fim") LocalDate fim);
}
