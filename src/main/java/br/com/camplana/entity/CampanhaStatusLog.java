package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Uma linha por mudança de status, inclusive a criação ({@code de} nulo). Só recebe INSERT. */
@Entity
@Immutable
@Table(name = "campanha_status_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampanhaStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "versao_id")
    private CampanhaVersao versao;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StatusCampanha de;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusCampanha para;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Instant em;

    @Column(columnDefinition = "text")
    private String justificativa;

    public CampanhaStatusLog(Campanha campanha, CampanhaVersao versao, StatusCampanha de, StatusCampanha para,
                             Usuario usuario, Instant em, String justificativa) {
        this.campanha = campanha;
        this.versao = versao;
        this.de = de;
        this.para = para;
        this.usuario = usuario;
        this.em = em;
        this.justificativa = justificativa;
    }
}
