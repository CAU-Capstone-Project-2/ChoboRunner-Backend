package capstone2.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

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
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API-KEY")
                                .in(SecurityScheme.In.HEADER)
                                .description("Authorization 헤더에 API key를 입력하세요. (Bearer 접두사는 Swagger UI가 자동으로 붙입니다)")));
    }
}


