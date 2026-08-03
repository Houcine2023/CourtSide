package com.courtside.api.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Redis cache configuration.
 *
 * @EnableCaching turns @Cacheable/@CacheEvict into real behaviour — without it the
 * annotations are inert (same silent-no-op family as @EnableScheduling and
 * @EnableMethodSecurity).
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {

    /**
     * Values are stored as JSON rather than Java-serialised bytes: readable in
     * redis-cli, language-agnostic, and it survives a class rename.
     *
     * The ObjectMapper needs two things Spring's default cache mapper lacks:
     *  - JavaTimeModule, or OffsetDateTime cannot be written at all;
     *  - type information, or Jackson cannot rebuild the concrete record on read.
     * The PolymorphicTypeValidator restricts that type info to OUR packages —
     * deserialising arbitrary class names from a cache is a known RCE vector.
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType("com.courtside.api")
                                .allowIfSubType("com.courtside.api")
                                .allowIfSubType("java.util")
                                .allowIfSubType("java.time")
                                .allowIfSubType("java.math")
                                .build(),
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);

        return RedisCacheConfiguration.defaultCacheConfig()
                // Short TTL: availability changes constantly. Even 30s absorbs the
                // burst of a page refresh while keeping the grid essentially live —
                // and every booking evicts the entry anyway (see BookingService).
                .entryTtl(Duration.ofSeconds(30))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }
}
