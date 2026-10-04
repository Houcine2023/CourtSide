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
import com.fasterxml.jackson.databind.SerializationFeature;
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
     * THE TYPE ID MATTERS. GenericJackson2JsonRedisSerializer reads back into
     * {@code Object.class}, so Jackson can only rebuild the right class if every
     * value in the JSON carries an "@class" marker. Whether it does is decided by
     * the DefaultTyping mode:
     *
     * <ul>
     *   <li>{@code NON_FINAL} skips final classes — and a Java {@code record} is
     *       implicitly final. That combination (records + NON_FINAL) produces JSON
     *       with no type id, and the read then dies with
     *       {@code InvalidTypeIdException: missing type id property '@class'}.
     *       It fails only on the SECOND read of a key, which makes it look flaky.</li>
     *   <li>{@code EVERYTHING} marks every non-primitive value, records included.</li>
     * </ul>
     *
     * The PolymorphicTypeValidator restricts which classes may be named in that
     * marker, because rebuilding an arbitrary class from cache content is a known
     * deserialisation-gadget vector. "java.lang" is required: String, Long and
     * Boolean are all values here.
     *
     * JavaTimeModule is not optional — without it OffsetDateTime cannot be written
     * at all.
     */
    static ObjectMapper cacheObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // ISO-8601 strings, not epoch arrays: readable in redis-cli and
                // unambiguous about the zone.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType("com.courtside.api")
                                .allowIfSubType("com.courtside.api")
                                .allowIfSubType("java.util")
                                .allowIfSubType("java.time")
                                .allowIfSubType("java.math")
                                .allowIfSubType("java.lang")
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);
    }

    static GenericJackson2JsonRedisSerializer valueSerializer() {
        return new GenericJackson2JsonRedisSerializer(cacheObjectMapper());
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // Short TTL: availability changes constantly. Even 30s absorbs the
                // burst of a page refresh while keeping the grid essentially live —
                // and every booking evicts the entry anyway (see BookingService).
                .entryTtl(Duration.ofSeconds(30))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer()));
    }
}
