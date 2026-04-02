package com.untitles.global.config.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // earliest: Consumer가 처음 구독할 때 가장 오래된 메시지부터 처리
        // latest로 바꾸면 구독 시작 이후 메시지만 처리
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // 신뢰할 패키지 지정: 이 패키지 하위 클래스만 역직렬화 허용 (보안)
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.untitles.*");
        // 타입 헤더 없이 역직렬화: Producer에서 ADD_TYPE_INFO_HEADERS=false로 설정했기 때문
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        // 역직렬화 기본 타입: 타입 헤더가 없을 때 이 타입으로 변환
        // @KafkaListener에서 @Payload로 각 이벤트 타입을 지정하므로 Object로 설정
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.lang.Object");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * ConcurrentKafkaListenerContainerFactory: @KafkaListener 어노테이션이 동작하려면 반드시 필요
     * concurrency 설정으로 Consumer 스레드 수 조절 가능 (기본 1)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
