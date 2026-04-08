package com.coruja.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnalisePassagensRequestDTO {
    private String placaAlvo;
    private int tempoMinutos;
    private List<RadarDTO> passagensSelecionadas;
}
