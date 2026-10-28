package br.com.camplana.repository;

import br.com.camplana.entity.MensagemChat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MensagemChatRepository extends JpaRepository<MensagemChat, Long> {

    @EntityGraph(attributePaths = {"usuario"})
    List<MensagemChat> findByChatIdOrderByCriadaEmAscIdAsc(String chatId);

    @EntityGraph(attributePaths = {"usuario"})
    Optional<MensagemChat> findFirstByChatIdOrderByCriadaEmAscIdAsc(String chatId);

    @EntityGraph(attributePaths = {"usuario"})
    List<MensagemChat> findByChatIdAndPapelAndIdGreaterThanOrderByCriadaEmAscIdAsc(String chatId, String papel, Long id);
}
