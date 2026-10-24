package br.com.camplana.repository;

import br.com.camplana.entity.SolicitacaoAlteracao;
import br.com.camplana.entity.StatusSolicitacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoAlteracaoRepository extends JpaRepository<SolicitacaoAlteracao, Integer> {

    /**
     * Lê a solicitação com SELECT ... FOR UPDATE. Usado só na decisão: serializa duas decisões
     * simultâneas da mesma solicitação, e a segunda enxerga o status já decidido (409).
     * Precisa rodar dentro de uma transação.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitacaoAlteracao s where s.id = :id")
    Optional<SolicitacaoAlteracao> findByIdForUpdate(@Param("id") Integer id);

    /** Fila do supervisor: pendentes que ele NÃO abriu, da mais antiga para a mais nova. */
    @EntityGraph(attributePaths = {"autor", "decisor"})
    List<SolicitacaoAlteracao> findByStatusAndAutorIdNotOrderByCriadaEmAsc(StatusSolicitacao status, Integer autorId);
}
