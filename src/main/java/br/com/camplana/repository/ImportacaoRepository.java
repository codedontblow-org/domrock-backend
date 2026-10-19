package br.com.camplana.repository;

import br.com.camplana.entity.Importacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    boolean existsByStatus(String status);

    List<Importacao> findByStatus(String status);

    @Query("SELECT i FROM Importacao i LEFT JOIN FETCH i.usuario WHERE i.id = :id")
    Optional<Importacao> findByIdWithUsuario(@Param("id") Long id);

    @Query("SELECT i FROM Importacao i LEFT JOIN FETCH i.usuario ORDER BY i.criadaEm DESC")
    List<Importacao> findAllOrderByCriadaEmDesc();
}
