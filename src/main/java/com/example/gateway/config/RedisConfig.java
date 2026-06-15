package com.example.gateway.config;

import com.example.gateway.model.InferenceResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, InferenceResponse> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<InferenceResponse> valueSerializer = new Jackson2JsonRedisSerializer<>(InferenceResponse.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, InferenceResponse> builder =
                RedisSerializationContext.newSerializationContext(keySerializer);

        RedisSerializationContext<String, InferenceResponse> context = builder.value(valueSerializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
