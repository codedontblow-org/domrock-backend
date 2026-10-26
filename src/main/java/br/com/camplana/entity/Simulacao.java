package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Simulação como job assíncrono com status e registro de toda simulação (A6-66). */
@Entity
@Table(name = "simulacao")
@Getter
@Setter
@NoArgsConstructor
public class Simulacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "chat_id", nullable = false, length = 64)
    private String chatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    // Só na re-simulação do Supervisor (A6-68, contrato 5.9): a versão re-simulada. Aí campanha fica nula.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "versao_id")
    private CampanhaVersao versao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSimulacao status;

    @Column(length = 50)
    private String etapa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String ficha;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String resultado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String trilha;

    @Column(name = "codigo_gerado", columnDefinition = "text")
    private String codigoGerado;

    @Column(length = 100)
    private String modelo;

    @Column
    private Integer tentativas;

    @Column(name = "duracao_ms")
    private Long duracaoMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String erro;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "concluida_em")
    private Instant concluidaEm;
}
