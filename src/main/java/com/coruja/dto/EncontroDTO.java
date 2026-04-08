package com.coruja.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EncontroDTO {
    private String concessionaria;
    private String rodovia;
    private String praca;
    private String km;
    private String sentido;
    private LocalDate data;
    private String horaAlvo;
    private String horaSuspeito;
    private long diferencaSegundos;
}
