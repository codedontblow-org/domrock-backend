package br.com.camplana.repository;

import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;

@Repository
public interface CampanhaVersaoRepository extends JpaRepository<CampanhaVersao, Long> {

    /** Contexto A6-74: última versão, período inclusivo e exclusão da própria conversa. */
    @Query("""
            SELECT v FROM CampanhaVersao v JOIN FETCH v.campanha c JOIN FETCH c.autor
            WHERE c.status IN :status AND c.chatId <> :chatId
              AND v.dataInicio <= :fim AND v.dataFim >= :inicio
              AND v.numero = (SELECT MAX(v2.numero) FROM CampanhaVersao v2 WHERE v2.campanha = c)
            ORDER BY v.id
            """)
    List<CampanhaVersao> findContextoSobreposto(@Param("inicio") LocalDate inicio,
                                               @Param("fim") LocalDate fim,
                                               @Param("chatId") String chatId,
                                               @Param("status") List<StatusCampanha> status);

    List<CampanhaVersao> findByCampanhaIdOrderByNumeroAsc(Long campanhaId);

    boolean existsByCampanhaId(Long campanhaId);

    /** Campanhas Passadas: a última versão de cada campanha no status pedido, do início mais recente. */
    @Query("""
            SELECT v FROM CampanhaVersao v JOIN FETCH v.campanha c JOIN FETCH c.autor
            WHERE c.status = :status
              AND v.numero = (SELECT MAX(v2.numero) FROM CampanhaVersao v2 WHERE v2.campanha = c)
            ORDER BY v.dataInicio DESC, v.id DESC
            """)
    List<CampanhaVersao> findUltimasPorStatus(@Param("status") StatusCampanha status);

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
