package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "funcionario_cargo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FuncionarioCargo {

    @EmbeddedId
    private FuncionarioCargoId id = new FuncionarioCargoId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("funcionarioId")
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("cargoId")
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;
}