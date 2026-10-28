package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Mensagem da conversa (A6-65). Pode ser do usuário, da Lana ou do sistema (status ou simulação).
 * Imutável: só recebe INSERT.
 */
@Entity
@Immutable
@Table(name = "mensagem_chat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MensagemChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, length = 64)
    private String chatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 20)
    private String papel;

    @Column(length = 20)
    private String tipo;

    @Column(name = "simulacao_id")
    private Long simulacaoId;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String ficha;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String trilha;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    public MensagemChat(String chatId, Usuario usuario, String papel, String tipo, Long simulacaoId,
                        String texto, String ficha, String trilha, Instant criadaEm) {
        this.chatId = chatId;
        this.usuario = usuario;
        this.papel = papel;
        this.tipo = tipo;
        this.simulacaoId = simulacaoId;
        this.texto = texto;
        this.ficha = ficha;
        this.trilha = trilha;
        this.criadaEm = criadaEm;
    }
}
