package com.coruja.eureka;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Startup
public class EurekaRegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(EurekaRegistrationService.class);

    @ConfigProperty(name = "quarkus.application.name")
    String appName;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "eureka.client.serviceUrl.defaultZone", defaultValue = "http://localhost:8761/eureka/")
    String eurekaUrl;

    private String instanceId;
    private String registroPayload;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private ScheduledExecutorService scheduler;

    void onStart(@Observes StartupEvent ev) {
        String appNameUpper = appName.toUpperCase();

        // Pega o IP real do container na rede do Docker (a correção mágica!)
        String ipAddress = "127.0.0.1";
        String hostname = "localhost";
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            ipAddress = localHost.getHostAddress();
            hostname = localHost.getHostName();
        } catch (Exception e) {
            LOG.warn("Não foi possível determinar o IP local, usando 127.0.0.1");
        }

        this.instanceId = hostname + ":" + appNameUpper + ":" + port;

        // Monta o payload JSON exato que o Eureka espera
        String payload = """
            {
               "instance": {
                  "instanceId": "%s",
                  "hostName": "%s",
                  "app": "%s",
                  "ipAddr": "%s",
                  "status": "UP",
                  "port": {"$": %d, "@enabled": "true"},
                  "vipAddress": "%s",
                  "dataCenterInfo": {
                     "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                     "name": "MyOwn"
                  }
               }
            }
            """.formatted(instanceId, ipAddress, appNameUpper, ipAddress, port, appNameUpper);

        this.registroPayload = payload;

        registrarNoEureka(payload, appNameUpper);
        iniciarHeartbeat(appNameUpper);
    }

    private void registrarNoEureka(String payload, String appNameUpper) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(eurekaUrl + "apps/" + appNameUpper))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                LOG.info("✅ Registado no Eureka com sucesso! Instância: {}", instanceId);
            } else {
                LOG.error("❌ Falha ao registar no Eureka. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.error("❌ Erro de conexão ao tentar registar no Eureka: {}", e.getMessage());
        }
    }

    private void iniciarHeartbeat(String appNameUpper) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(eurekaUrl + "apps/" + appNameUpper + "/" + instanceId))
                        .timeout(Duration.ofSeconds(3))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 404) {
                    // SE O EUREKA NÃO NOS CONHECE MAIS, FORÇAMOS UM NOVO REGISTRO
                    LOG.warn("⚠️ Eureka não reconheceu o heartbeat (Status 404). Forçando novo registro...");
                    registrarNoEureka(this.registroPayload, appNameUpper);
                } else if (response.statusCode() != 200) {
                    // TRATA OUTROS ERROS POSSÍVEIS (500, etc)
                    LOG.warn("⚠️ Falha ao enviar heartbeat para o Eureka (Status {}).", response.statusCode());
                }
            } catch (Exception e) {
                LOG.debug("Falha ao enviar heartbeat para o Eureka: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS); // Envia a cada 30 segundos
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        try {
            String appNameUpper = appName.toUpperCase();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(eurekaUrl + "apps/" + appNameUpper + "/" + instanceId))
                    .timeout(Duration.ofSeconds(2))
                    .DELETE()
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            LOG.info("🛑 Instância removida do Eureka com sucesso.");
        } catch (Exception e) {
            LOG.error("Erro ao remover instância do Eureka no encerramento: {}", e.getMessage());
        }
    }
}
