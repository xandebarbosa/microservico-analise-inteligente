package com.coruja.dto;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class VeiculoSuspeitoDTO {
    private String placa;
    private int quantidadeEncontros;
    private List<EncontroDTO> locaisDeEncontro = new ArrayList<>();

    public void adicionarEncontro(EncontroDTO encontro) {
        this.locaisDeEncontro.add(encontro);
        this.quantidadeEncontros++;
    }
}
