package com.courtside.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import com.courtside.api.dtos.AvailabilityResponse;

/**
 * Guards the one thing that is easy to break and invisible until it bites in
 * production: the availability grid must survive a Redis round trip.
 *
 * The historical bug: CacheConfig used DefaultTyping.NON_FINAL, which omits the
 * "@class" marker for final classes — and a Java record is implicitly final. The
 * write looked fine; the SECOND read of the same key blew up with
 * "missing type id property '@class'". These tests assert the round trip directly
 * so that regression cannot come back.
 */
@DisplayName("CacheConfig — availability value serialization")
class CacheConfigSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer = CacheConfig.valueSerializer();

    @Test
    @DisplayName("an availability grid round-trips through Redis unchanged")
    void roundTripsAvailabilityGrid() {
        AvailabilityResponse original = sampleResponse();

        AvailabilityResponse restored = deserialize(serialize(original));

        assertThat(restored).isNotNull();
        assertThat(restored.courtId()).isEqualTo(original.courtId());
        assertThat(restored.clubId()).isEqualTo(original.clubId());
        assertThat(restored.courtName()).isEqualTo(original.courtName());
        assertThat(restored.date()).isEqualTo(original.date());
        assertThat(restored.clubOpen()).isEqualTo(original.clubOpen());
        assertThat(restored.slots()).hasSameSizeAs(original.slots());
    }

    @Test
    @DisplayName("nested slot records keep their type, times and price")
    void roundTripsSlots() {
        AvailabilityResponse restored = deserialize(serialize(sampleResponse()));

        AvailabilityResponse.Slot slot = restored.slots().get(0);
        assertThat(slot).isInstanceOf(AvailabilityResponse.Slot.class);
        assertThat(slot.start()).isEqualTo("2026-08-15T08:00Z");
        assertThat(slot.end()).isEqualTo("2026-08-15T09:30Z");
        assertThat(slot.available()).isTrue();
        assertThat(slot.price()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @DisplayName("a closed club returns an empty slot list, not null")
    void roundTripsClosedDay() {
        AvailabilityResponse closed = new AvailabilityResponse(
                7L, 2L, "Riverside Padel", LocalDate.of(2026, 8, 16), false, List.of());

        AvailabilityResponse restored = deserialize(serialize(closed));

        assertThat(restored.clubOpen()).isFalse();
        assertThat(restored.slots()).isEmpty();
    }

    @Test
    @DisplayName("a null cache value is not cached")
    void nullIsNotCached() {
        // disableCachingNullValues() is what keeps a transient failure from being
        // pinned in Redis for the whole TTL.
        assertThat(new CacheConfig().cacheConfiguration().getAllowCacheNullValues()).isFalse();
    }

    private byte[] serialize(AvailabilityResponse response) {
        return serializer.serialize(response);
    }

    @SuppressWarnings("unchecked")
    private AvailabilityResponse deserialize(byte[] bytes) {
        return (AvailabilityResponse) serializer.deserialize(bytes);
    }

    private static AvailabilityResponse sampleResponse() {
        AvailabilityResponse.Slot slot = new AvailabilityResponse.Slot(
                OffsetDateTime.parse("2026-08-15T08:00Z"),
                OffsetDateTime.parse("2026-08-15T09:30Z"),
                true,
                new BigDecimal("45.00"));

        return new AvailabilityResponse(
                5L,
                2L,
                "Riverside Padel",
                LocalDate.of(2026, 8, 15),
                true,
                List.of(slot));
    }
}
