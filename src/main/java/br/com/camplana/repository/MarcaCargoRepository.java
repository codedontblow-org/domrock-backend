package br.com.camplana.repository;

import br.com.camplana.entity.MarcaCargo;
import br.com.camplana.entity.MarcaCargoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaCargoRepository extends JpaRepository<MarcaCargo, MarcaCargoId> {
}