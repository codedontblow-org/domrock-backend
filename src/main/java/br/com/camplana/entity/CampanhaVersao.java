package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * O que o autor viu ao submeter: ficha e resultado copiados da simulação, congelados. Imutável
 * (só INSERT); o status e a decisão da versão saem do {@link CampanhaStatusLog}.
 */
@Entity
@Immutable
@Table(name = "campanha_versao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampanhaVersao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @Column(nullable = false)
    private int numero;

    @Column(name = "simulacao_id", nullable = false)
    private Long simulacaoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String regra;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String resultado;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "ultima_mensagem_id")
    private Long ultimaMensagemId;

    @Column(name = "submetida_em", nullable = false)
    private Instant submetidaEm;

    public CampanhaVersao(Campanha campanha, int numero, Long simulacaoId, String regra, String resultado,
                          LocalDate dataInicio, LocalDate dataFim, Instant submetidaEm) {
        this(campanha, numero, simulacaoId, regra, resultado, dataInicio, dataFim, null, submetidaEm);
    }

    public CampanhaVersao(Campanha campanha, int numero, Long simulacaoId, String regra, String resultado,
                          LocalDate dataInicio, LocalDate dataFim, Long ultimaMensagemId, Instant submetidaEm) {
        this.campanha = campanha;
        this.numero = numero;
        this.simulacaoId = simulacaoId;
        this.regra = regra;
        this.resultado = resultado;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.ultimaMensagemId = ultimaMensagemId;
        this.submetidaEm = submetidaEm;
    }
}
