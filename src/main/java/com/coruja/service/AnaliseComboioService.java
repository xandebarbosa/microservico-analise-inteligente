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

        log.info("Iniciando a análise de comboio para a placa {} na data {}", placaAlvo, data);

        // 1. Busca os passos do alvo
        RadarPageDTO passagensAlvo = bffClient.buscarPassagensAlvo(placaAlvo, 1000);

        if (passagensAlvo == null || passagensAlvo.getContent() == null || passagensAlvo.getContent().isEmpty()) {
            log.warn("Nenhuma passagem encontrada para a placa alvo: {}", placaAlvo);
            return Collections.emptyList();
        }

        //Filtra apenas as passagens do dia especificado
        List<RadarDTO> passagensDoDia = passagensAlvo.getContent()
                .stream()
                .filter(p -> p.getData().equals(data))
                .collect(Collectors.toList());

        if (passagensDoDia.isEmpty()) {
            log.warn("Nenhuma passagem encontrada para a placa {} na data {}", placaAlvo, data);
            return Collections.emptyList();
        }

        log.info("Placa alvo {} encontrada em {} locais na data {}", placaAlvo, passagensDoDia.size(), data);

        Map<String, VeiculoSuspeitoDTO> suspeitoDTOMap = new HashMap<>();

        // 2. Para cada local que o alvo passou, procuramos companhias
        for (RadarDTO alvo : passagensDoDia) {

            log.debug("Analisando local: {} KM {} às {}", alvo.getRodovia(), alvo.getKm(), alvo.getHora());

            // Janela de tempo configuravel (ex: alvo passou 10:00. Busca de 09:59:40 até 10:00:20)
            LocalTime horaInicio = alvo.getHora().minusMinutes(tempoMinutos);
            LocalTime horaFim =  alvo.getHora().plusMinutes(tempoMinutos);

            RadarPageDTO passagensNoLocal = null;

            try {
                passagensNoLocal = bffClient.buscarPassagensPorLocalETempo(
                        data, alvo.getRodovia(), alvo.getPraca() ,alvo.getKm(), alvo.getSentido(), horaInicio, horaFim, 5000
                );
            } catch (Exception e) {
                log.warn("Erro ao buscar passagens no local {} - continuando", alvo.getRodovia(), e);
                continue;
            }

            if (passagensNoLocal == null || passagensNoLocal.getContent() == null) {
                log.debug("Nenhuma passagem encontrada neste local");
                continue;
            };

            log.debug("Encontradas {} passagens no local", passagensNoLocal.getContent().size());

            // 3. Processa e agrupa os suspeitos
            for (RadarDTO suspeito : passagensNoLocal.getContent()) {

                //Ignora a própria placa alvo
                if (suspeito.getPlaca() != null && suspeito.getPlaca().trim().toUpperCase().equals(placaAlvo.trim().toUpperCase())) {
                    continue;
                }

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

                // Trata a placa: remove espaços e força MAIÚSCULA
                String placaTratada = suspeito.getPlaca() != null ?
                        suspeito.getPlaca().trim().toUpperCase() : "DESCONHECIDO";

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

        // 4. Retorna ordenado (quem andou junto mais vezes, aparece no topo)
        List<VeiculoSuspeitoDTO> resultado =  suspeitoDTOMap.values().stream()
                .filter(s -> s.getQuantidadeEncontros() >= 2) // Filtro: Tem que ter encontrado mais de 1 vez para ser suspeito
                .sorted(Comparator.comparingInt(VeiculoSuspeitoDTO::getQuantidadeEncontros).reversed().thenComparing(VeiculoSuspeitoDTO::getPlaca))
                .collect(Collectors.toList());

        log.info("Análise concluída: {} placas diferentes encontradas", resultado.size());
        return resultado;
    }

    /**
     * MÉTODO 2: ANÁLISE DE COMBOIO SELETIVA (Avançada, baseada nas passagens selecionadas na tela)
     */
    public List<VeiculoSuspeitoDTO> analisarComboioAvancado(ComboioAvancadoRequestDTO request) {
        log.info("Iniciando análise avançada para a placa {} com {} passagens selecionadas", request.getPlacaAlvo(), request.getPassagens().size());

        if (request.getPassagens() == null || request.getPassagens().isEmpty()) {
            log.warn("Nenhuma passagem fornecida para análise avançada");
            return Collections.emptyList();
        }

        Map<String, VeiculoSuspeitoDTO> suspeitoDTOMap = new HashMap<>();

        // Itera EXATAMENTE sobre as passagens que o usuário selecionou na tela
        for (RadarDTO alvo : request.getPassagens()) {
            log.debug("Processando passagem selecionada: {} às {}", alvo.getRodovia(), alvo.getHora());

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
                log.warn("⚠️ Falha ou Timeout no BFF ao buscar o local {} ({}). Ignorando e continuando...", alvo.getRodovia(), alvo.getPraca(), e);
                continue;
            }

            if (passagensNoLocal == null || passagensNoLocal.getContent() == null) {
                log.debug("Nenhuma passagem neste local específico");
                continue;
            };

            log.debug("Encontradas {} passagens nesta localidade", passagensNoLocal.getContent().size());

            for (RadarDTO suspeito : passagensNoLocal.getContent()) {
                if (suspeito.getPlaca() == null || suspeito.getPlaca().trim().equalsIgnoreCase(request.getPlacaAlvo().trim())) continue;

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
                String placaTratada = suspeito.getPlaca().trim().toUpperCase();

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
        //Retorna todas as placas encontradas
        List<VeiculoSuspeitoDTO> resultado = suspeitoDTOMap.values().stream()
                .filter(s -> s.getQuantidadeEncontros() >= 2)
                .sorted(Comparator.comparingInt(VeiculoSuspeitoDTO::getQuantidadeEncontros).reversed().thenComparing(VeiculoSuspeitoDTO::getPlaca))
                .collect(Collectors.toList());

        log.info("Análise avançada concluída: {} placas diferentes encontradas", resultado.size());
        return resultado;
    }
}
