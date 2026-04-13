package capstone2.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(
            @Value("${openapi.server-https-url}") String httpsServerUrl,
            @Value("${openapi.server-local-url}") String localServerUrl,
            @Value("${spring.application.name}") String applicationName
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .version("v1")
                        .description("ChoboRunner API documentation for the server"))
                .servers(List.of(
                        new Server().url(httpsServerUrl).description("HTTPS API Server"),
                        new Server().url(localServerUrl).description("Local API Server")
                ));
    }
}


