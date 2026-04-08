package com.coruja.dto;

import lombok.Data;

import java.util.List;

@Data
public class ComboioAvancadoRequestDTO {
    private String placaAlvo;
    private int tempoMinutos;
    private List<RadarDTO> passagens;
}
