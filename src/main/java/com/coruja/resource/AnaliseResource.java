package com.coruja.resource;

import com.coruja.dto.VeiculoSuspeitoDTO;
import com.coruja.service.AnaliseComboioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Path("/analise")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnaliseResource {

    @Inject
    AnaliseComboioService analiseComboioService;

    @GET
    @Path("/comboio")
    @Operation(summary = "Detecta veículos que viajaram em comboio com uma placa alvo")
    public Response detectarComobio(
            @QueryParam("placaAlvo") String placaAlvo,
            @QueryParam("data") String dataStr,
            @QueryParam("tempo") @DefaultValue("30") int tempoMinutos
    ) {
        LocalDate data;

        try {
            // Verifica se a data começa com o dia (ex: 05/03/2026 ou 05-03-2026)
            if (dataStr.matches("^\\d{2}[/-]\\d{2}[/-]\\d{4}$")) {
                DateTimeFormatter formatterBR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                // Normaliza para barras caso venha com hífen no padrão BR
                data = LocalDate.parse(dataStr.replace("-", "/"), formatterBR);
            } else {
                // Fallback para o padrão internacional (YYYY-MM-DD ou YYYY/MM/DD)
                data = LocalDate.parse(dataStr.replace("/", "-"));
            }
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Formato de data inválido. Utilize DD/MM/YYYY ou YYYY-MM-DD.")
                    .build();
        }

        List<VeiculoSuspeitoDTO> resultado = analiseComboioService.analisarComboio(placaAlvo, data, tempoMinutos);

        return  Response.ok(resultado).build();
    }
}
