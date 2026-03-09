package com.coruja.service;

import com.coruja.client.BffRestClient;
import com.coruja.dto.EncontroDTO;
import com.coruja.dto.RadarDTO;
import com.coruja.dto.RadarPageDTO;
import com.coruja.dto.VeiculoSuspeitoDTO;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnaliseComboioService {

    @RestClient
    BffRestClient bffClient;

    public List<VeiculoSuspeitoDTO> analisarComboio(String placaAlvo, LocalDate data, int tempoMinutos) {
        // 1. Busca os passos do alvo
        RadarPageDTO passagensAlvo = bffClient.buscarPassagensAlvo(placaAlvo, 1000);

        if (passagensAlvo == null || passagensAlvo.getContent() == null) {
            return Collections.emptyList();
        }

        List<RadarDTO> passagensDoDia = passagensAlvo.getContent()
                .stream()
                .filter(p -> p.getData().equals(data))
                .collect(Collectors.toList());

        Map<String, VeiculoSuspeitoDTO> suspeitoDTOMap = new HashMap<>();

        // 2. Para cada local que o alvo passou, procuramos companhias
        for (RadarDTO alvo : passagensDoDia) {

            // Janela de tempo configuravel (ex: alvo passou 10:00. Busca de 09:59:40 até 10:00:20)
            LocalTime horaInicio = alvo.getHora().minusMinutes(tempoMinutos);
            LocalTime horaFim =  alvo.getHora().plusMinutes(tempoMinutos);

            RadarPageDTO passagensNoLocal = bffClient.buscarPassagensPorLocalETempo(
                    data, alvo.getRodovia(), alvo.getPraca(), alvo.getKm(), alvo.getSentido(), horaInicio, horaFim, 5000
            );

            if (passagensNoLocal == null || passagensNoLocal.getContent() == null) continue;

            // 3. Processa e agrupa os suspeitos
            for (RadarDTO suspeito : passagensNoLocal.getContent()) {
                if (suspeito.getPlaca().equalsIgnoreCase(placaAlvo)) continue;

                long diferencaSeg = Math.abs(ChronoUnit.SECONDS.between(alvo.getHora(), suspeito.getHora()));

                EncontroDTO encontroDTO = new EncontroDTO();
                encontroDTO.setConcessionaria(suspeito.getConcessionaria());
                encontroDTO.setRodovia(suspeito.getRodovia());;
                encontroDTO.setPraca(suspeito.getPraca());
                encontroDTO.setKm(suspeito.getKm());
                encontroDTO.setSentido(suspeito.getSentido());
                encontroDTO.setHoraAlvo(alvo.getHora().toString());
                encontroDTO.setHoraSuspeito(suspeito.getHora().toString());
                encontroDTO.setDiferencaSegundos(diferencaSeg);

                VeiculoSuspeitoDTO suspeitoDTO = suspeitoDTOMap.computeIfAbsent(
                        suspeito.getPlaca(), k -> {
                            VeiculoSuspeitoDTO dto = new VeiculoSuspeitoDTO();
                            dto.setPlaca(suspeito.getPlaca());
                            return dto;
                        }
                );
                suspeitoDTO.adicionarEncontro(encontroDTO);
            }
        }

        // 4. Retorna ordenado (quem andou junto mais vezes, aparece no topo)
        return suspeitoDTOMap.values().stream()
                .filter(s -> s.getQuantidadeEncontros() > 1) // Filtro: Tem que ter encontrado mais de 1 vez para ser suspeito
                .sorted(Comparator.comparingInt(VeiculoSuspeitoDTO::getQuantidadeEncontros).reversed())
                .collect(Collectors.toList());

    }
}
