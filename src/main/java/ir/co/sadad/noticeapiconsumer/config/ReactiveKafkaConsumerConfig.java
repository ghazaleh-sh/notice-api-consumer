package ir.co.sadad.noticeapiconsumer.config;

import ir.co.sadad.noticeapiconsumer.dtos.TransactionNoticeReqDto;
import ir.co.sadad.noticeapiconsumer.service.ReactiveConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumer is  the service that will be responsible for reading messages
 * and processing them according to my business logic.
 *
 * @author g.shahrokhabadi
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReactiveKafkaConsumerConfig {

    @Value("${spring.kafka.consumer.topics[0]}")
    private String TOPIC;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private final ReactiveConsumerService consumerService;

    @Bean
    public ReceiverOptions<String, TransactionNoticeReqDto> kafkaReceiverOptions(KafkaProperties kafkaProperties) {

        final Map<String, Object> map = new HashMap<>(kafkaProperties.buildConsumerProperties());
        map.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        map.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 10_000L);
        map.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);

        log.info("Created Kafka consumer configuration: {}", map);

        ReceiverOptions<String, TransactionNoticeReqDto> basicReceiverOptions = ReceiverOptions.create(map);
        return basicReceiverOptions
                //balance between committing offsets frequently and minimizing offset loss
                .commitInterval(Duration.ofSeconds(5)) //commit offsets every 5 seconds, based on my latency and reliability needs
                .commitBatchSize(100)// the batch size based on my requirements
                .subscription(Collections.singletonList(TOPIC));

    }

    /**
     * The code segment below consumes records from Kafka topics,
     * transforms the record and sends the output to an external sink.
     * Kafka consumer offsets are committed after records are successfully output to sink.
     */
    @Bean
    public ReactiveKafkaConsumerTemplate<String, TransactionNoticeReqDto> reactiveKafkaConsumerTemplate(ReceiverOptions<String, TransactionNoticeReqDto> kafkaReceiverOptions) {
        log.info("Kafka consumer successfully created and connected to broker.");
        ReactiveKafkaConsumerTemplate<String, TransactionNoticeReqDto> consumerTemplate =
                new ReactiveKafkaConsumerTemplate<>(kafkaReceiverOptions);

        consumerTemplate.receive()
                //to introduce a delay between processing each element, it keeps incoming record and releases after the time set
                .delayElements(Duration.ofMillis(500)) // BACKPRESSURE,control the rate of message consumption and prevent overwhelming downstream systems.
                .doOnNext(consumerRecord -> {
                            log.info("received key={}, value={} from topic={}, offset={}",
                                    consumerRecord.key(),
                                    consumerRecord.value(),
                                    consumerRecord.topic(),
                                    consumerRecord.offset());

                            consumerRecord.receiverOffset().commit();
                        }
                )
                .map(ConsumerRecord::value)//to extract the value from the ConsumerRecord.
                .flatMap(consumerService::storeInDB)// to process each message concurrently.
                .doOnNext(data -> log.info("successfully consumed {}={}", TransactionNoticeReqDto.class.getSimpleName(), data))
                .doOnError(throwable -> log.error("something bad happened while consuming : {}", throwable.getMessage()))
                .subscribe();

        return consumerTemplate;

    }

}
