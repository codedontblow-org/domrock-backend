package br.com.camplana.repository;

import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.StatusCampanha;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

    /** SELECT ... FOR UPDATE: dois cliques em "Submeter" ao mesmo tempo, o segundo espera e recebe 409. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campanha c WHERE c.id = :id")
    Optional<Campanha> findParaAlterar(@Param("id") Long id);

    @Query("SELECT c FROM Campanha c JOIN FETCH c.autor WHERE c.id = :id")
    Optional<Campanha> findComAutor(@Param("id") Long id);

    List<Campanha> findByAutorIdAndStatusOrderByAtualizadaEmDesc(Integer autorId, StatusCampanha status);

    List<Campanha> findByAutorIdAndStatusNotOrderByAtualizadaEmDesc(Integer autorId, StatusCampanha status);
}
