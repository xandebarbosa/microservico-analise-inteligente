package com.coruja.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RadarDTO {
    private String placa;
    private LocalDate data;
    private LocalTime hora;
    private String rodovia;
    private String km;
    private String sentido;
    private String praca;
    private String concessionaria;
}
