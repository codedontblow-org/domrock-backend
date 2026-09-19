package br.com.camplana.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@jakarta.persistence.Embeddable
public class MarcaCargoId implements Serializable{

    private Integer marcaId;
    private Integer cargoId;
    private LocalDate dateRef;
}