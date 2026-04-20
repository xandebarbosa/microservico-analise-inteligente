package com.coruja.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RadarDTO {
    private String placa;
    private LocalDate data;
    private LocalTime hora;
    private String rodovia;
    private String km;
    private String sentido;
    private String praca;

    // Aceita tanto no plural quanto no singular
    @JsonAlias({"concessionarias", "concessionaria"})
    private String concessionaria;
}
