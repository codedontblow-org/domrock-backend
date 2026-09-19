package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "marca_cargo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarcaCargo {

    @EmbeddedId
    private MarcaCargoId id = new MarcaCargoId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("marcaId")
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("cargoId")
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    @Column(name = "pct_comiss", precision = 5, scale = 2, nullable = false)
    private BigDecimal pctComiss;
}