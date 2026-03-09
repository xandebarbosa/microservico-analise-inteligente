package com.coruja.dto;

import lombok.Data;

@Data
public class EncontroDTO {
    private String concessionaria;
    private String rodovia;
    private String praca;
    private String km;
    private String sentido;
    private String horaAlvo;
    private String horaSuspeito;
    private long diferencaSegundos;
}
