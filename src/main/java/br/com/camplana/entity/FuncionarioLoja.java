package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "funcionario_loja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FuncionarioLoja {

    @EmbeddedId
    private FuncionarioLojaId id = new FuncionarioLojaId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("funcionarioId")
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("lojaId")
    @JoinColumn(name = "loja_id")
    private Loja loja;
}