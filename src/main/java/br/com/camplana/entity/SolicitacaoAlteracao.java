package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitacao_alteracao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitacaoAlteracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 30, nullable = false)
    private TipoAlteracao tipo;

    @Column(name = "matricula", length = 20, nullable = false)
    private String matricula;

    @Column(name = "competencia", nullable = false)
    private LocalDate competencia;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_antes", columnDefinition = "jsonb", nullable = false)
    private String valorAntes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_depois", columnDefinition = "jsonb", nullable = false)
    private String valorDepois;

    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", length = 20, nullable = false)
    private OrigemSolicitacao origem;

    @Column(name = "chat_id", length = 100)
    private String chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decisor_id")
    private Usuario decisor;

    @Column(name = "decidida_em")
    private LocalDateTime decididaEm;

    @Column(name = "justificativa", columnDefinition = "text")
    private String justificativa;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private LocalDateTime criadaEm;

    @PrePersist
    public void prePersist() {
        if (this.criadaEm == null) {
            this.criadaEm = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = StatusSolicitacao.PENDENTE;
        }
    }
}
