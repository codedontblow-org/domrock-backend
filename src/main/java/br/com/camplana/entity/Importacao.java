package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "importacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Importacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 7)
    private String competencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String arquivos;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas;

    @Column(name = "linhas_importadas", nullable = false)
    private int linhasImportadas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String totais;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String pendencias;

    @Column(columnDefinition = "text")
    private String erro;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private LocalDateTime criadaEm;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @PrePersist
    public void prePersist() {
        if (this.criadaEm == null) {
            this.criadaEm = LocalDateTime.now();
        }
        if (this.arquivos == null) this.arquivos = "[]";
        if (this.totais == null) this.totais = "{}";
        if (this.pendencias == null) this.pendencias = "[]";
    }
}
