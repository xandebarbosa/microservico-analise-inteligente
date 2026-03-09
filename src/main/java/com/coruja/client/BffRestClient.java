package com.coruja.client;

import com.coruja.dto.RadarPageDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.LocalDate;
import java.time.LocalTime;

@RegisterRestClient(configKey = "bff-api")
@RegisterClientHeaders
public interface BffRestClient {

    @GET
    @Path("/radares/busca-placa")
    RadarPageDTO buscarPassagensAlvo(
            @QueryParam("placa") String placa,
            @QueryParam("size") int size
    );

    @GET
    @Path("/radares/busca-local")
    RadarPageDTO buscarPassagensPorLocalETempo(
            @QueryParam("data")LocalDate data,
            @QueryParam("rodovia") String rodovia,
            @QueryParam("praca") String praca,
            @QueryParam("km") String km,
            @QueryParam("sentido") String sentido,
            @QueryParam("horaInicial") LocalTime horaIncial,
            @QueryParam("horaFinal") LocalTime horaFinal,
            @QueryParam("size") int size
    );
}
