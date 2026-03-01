package ai.teneta.argus.queue.sqs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(SqsQueueProperties.class)
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(
            @Value("${cloud.aws.sqs.endpoint}") String endpoint,
            @Value("${cloud.aws.sqs.region}") String region) {
        return SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
