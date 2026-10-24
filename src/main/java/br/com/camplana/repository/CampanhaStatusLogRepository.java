package br.com.camplana.repository;

import br.com.camplana.entity.CampanhaStatusLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampanhaStatusLogRepository extends JpaRepository<CampanhaStatusLog, Long> {

    // id desempata linhas gravadas no mesmo instante (ex.: relógio fixo nos testes)
    @EntityGraph(attributePaths = {"usuario", "versao"})
    List<CampanhaStatusLog> findByCampanhaIdOrderByEmAscIdAsc(Long campanhaId);
}
