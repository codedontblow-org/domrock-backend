package br.com.camplana.repository;

import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampanhaVersaoRepository extends JpaRepository<CampanhaVersao, Long> {

    List<CampanhaVersao> findByCampanhaIdOrderByNumeroAsc(Long campanhaId);

    boolean existsByCampanhaId(Long campanhaId);

    /** Fila do Supervisor (5.5): a última versão de cada campanha no status pedido, menos as dele. */
    @Query("""
            SELECT v FROM CampanhaVersao v JOIN FETCH v.campanha c JOIN FETCH c.autor
            WHERE c.status = :status AND c.autor.id <> :supervisorId
              AND v.numero = (SELECT MAX(v2.numero) FROM CampanhaVersao v2 WHERE v2.campanha = c)
            ORDER BY v.submetidaEm ASC, v.id ASC
            """)
    List<CampanhaVersao> findUltimasPorStatusMenosDoAutor(@Param("status") StatusCampanha status,
                                                          @Param("supervisorId") Integer supervisorId);
}
