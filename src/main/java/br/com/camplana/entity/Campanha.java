package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Campanha salva (A6-64). A ficha e o resultado ficam na simulação e, ao submeter, na versão. */
@Entity
@Table(name = "campanha")
@Getter
@Setter
@NoArgsConstructor
public class Campanha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "chat_id", nullable = false, length = 64, unique = true)
    private String chatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusCampanha status;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;
}
