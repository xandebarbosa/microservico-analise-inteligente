package com.coruja.service;

import com.coruja.client.BffRestClient;
import com.coruja.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnaliseComboioService {

    private static final Logger log = LoggerFactory.getLogger(AnaliseComboioService.class);
    @RestClient
    BffRestClient bffClient;

    /**
     * MÉTODO 1: ANÁLISE DE COMBOIO TRADICIONAL (Automática por data)
     */
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
                    data, alvo.getRodovia(), alvo.getPraca() ,alvo.getKm(), alvo.getSentido(), horaInicio, horaFim, 5000
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
                encontroDTO.setData(suspeito.getData());
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

    /**
     * MÉTODO 2: ANÁLISE DE COMBOIO SELETIVA (Avançada, baseada nas passagens selecionadas na tela)
     */
    public List<VeiculoSuspeitoDTO> analisarComboioAvancado(ComboioAvancadoRequestDTO request) {
        if (request.getPassagens() == null || request.getPassagens().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, VeiculoSuspeitoDTO> suspeitoDTOMap = new HashMap<>();

        // Itera EXATAMENTE sobre as passagens que o usuário selecionou na tela
        for (RadarDTO alvo : request.getPassagens()) {
            LocalTime horaInicio = alvo.getHora().minusMinutes(request.getTempoMinutos());
            LocalTime horaFim = alvo.getHora().plusMinutes(request.getTempoMinutos());

            RadarPageDTO passagensNoLocal = null;

            try {
                // Tenta buscar as passagens neste local
                passagensNoLocal = bffClient.buscarPassagensPorLocalETempo(
                        alvo.getData(), alvo.getRodovia(), alvo.getPraca(), alvo.getKm(), alvo.getSentido(), horaInicio, horaFim, 5000
                );
            } catch (Exception e) {
                // 🔹 O SEGREDO ESTÁ AQUI!
                // Se o BFF der Timeout ou Erro 500 nesta praça específica, a IA não quebra mais.
                // Ela apenas avisa no console, ignora esse ponto e continua cruzando os dados das outras praças.
                System.err.println("⚠️ Falha ou Timeout no BFF ao buscar o local " + alvo.getRodovia() + ". Ignorando e continuando...");
                continue;
            }

            if (passagensNoLocal == null || passagensNoLocal.getContent() == null) continue;

            for (RadarDTO suspeito : passagensNoLocal.getContent()) {
                if (suspeito.getPlaca().equalsIgnoreCase(request.getPlacaAlvo())) continue;

                long diferencaSeg = Math.abs(ChronoUnit.SECONDS.between(alvo.getHora(), suspeito.getHora()));

                EncontroDTO encontroDTO = new EncontroDTO();
                encontroDTO.setConcessionaria(suspeito.getConcessionaria());
                encontroDTO.setRodovia(suspeito.getRodovia());
                encontroDTO.setPraca(suspeito.getPraca());
                encontroDTO.setKm(suspeito.getKm());
                encontroDTO.setSentido(suspeito.getSentido());
                encontroDTO.setData(alvo.getData()); // Necessário para o Excel
                encontroDTO.setHoraAlvo(alvo.getHora().toString());
                encontroDTO.setHoraSuspeito(suspeito.getHora().toString());
                encontroDTO.setDiferencaSegundos(diferencaSeg);

                // Limpamos os espaços em branco e forçamos MAIÚSCULO para garantir que o Java cruze os dados corretamente
                String placaTratada = suspeito.getPlaca() != null ? suspeito.getPlaca().trim().toUpperCase() : "DESCONHECIDO";

                VeiculoSuspeitoDTO suspeitoDTO = suspeitoDTOMap.computeIfAbsent(
                        placaTratada, k -> {
                            VeiculoSuspeitoDTO dto = new VeiculoSuspeitoDTO();
                            dto.setPlaca(placaTratada);
                            return dto;
                        }
                );
                suspeitoDTO.adicionarEncontro(encontroDTO);
            }
        }

        // Para busca seletiva, consideramos suspeito qualquer um que tenha sido visto nos pontos selecionados (> 0)
        return suspeitoDTOMap.values().stream()
                .filter(s -> s.getQuantidadeEncontros() > 1)
                .sorted(Comparator.comparingInt(VeiculoSuspeitoDTO::getQuantidadeEncontros).reversed())
                .collect(Collectors.toList());
    }
}
