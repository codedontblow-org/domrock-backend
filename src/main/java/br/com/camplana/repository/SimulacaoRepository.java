package br.com.camplana.repository;

import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SimulacaoRepository extends JpaRepository<Simulacao, Long> {

    Optional<Simulacao> findFirstByCampanhaIdAndStatusOrderByConcluidaEmDescIdDesc(
            Long campanhaId, StatusSimulacao status);

    Optional<Simulacao> findFirstByCampanhaIdAndStatusInOrderByIdDesc(
            Long campanhaId, Collection<StatusSimulacao> statuses);

    Optional<Simulacao> findFirstByChatIdOrderByIdAsc(String chatId);

    /** Última re-simulação concluída da versão (contrato 5.6, {@code resimulacao}), com quem pediu. */
    @EntityGraph(attributePaths = "usuario")
    Optional<Simulacao> findFirstByVersaoIdAndStatusOrderByConcluidaEmDescIdDesc(Long versaoId, StatusSimulacao status);

    List<Simulacao> findByStatusIn(Collection<StatusSimulacao> statuses);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE simulacao SET campanha_id = :campanhaId WHERE chat_id = :chatId", nativeQuery = true)
    void vincularConversa(@Param("chatId") String chatId, @Param("campanhaId") Long campanhaId);
}
