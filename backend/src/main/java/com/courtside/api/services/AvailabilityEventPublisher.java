package com.courtside.api.services;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.courtside.api.dtos.AvailabilityEvent;
import com.courtside.api.entities.Booking;

/**
 * Publishes availability changes to everyone watching a club.
 *
 * The subtle part is WHEN. Broadcasting inside the transaction would announce a
 * booking that a later rollback erases — subscribers would grey out a slot that is
 * actually free, and nothing would ever correct them. So the send is deferred until
 * after a successful commit.
 */
@Service
public class AvailabilityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.timezone:Africa/Tunis}")
    private String timezone;

    public AvailabilityEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(Booking booking, AvailabilityEvent.Type type) {
        Long clubId = booking.getCourt().getClub().getId();
        LocalDate date = booking.getStartTime()
                .atZoneSameInstant(ZoneId.of(timezone))
                .toLocalDate();

        AvailabilityEvent event = new AvailabilityEvent(
                type, clubId, booking.getCourt().getId(), date,
                booking.getStartTime(), booking.getEndTime());

        // One topic per club: a client watching Padel Lac must not be woken by every
        // booking in the country. Fine-grained topics are how you keep broadcast cheap.
        String destination = "/topic/clubs/" + clubId + "/availability";

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(destination, event);
                }
            });
        } else {
            send(destination, event);
        }
    }

    private void send(String destination, AvailabilityEvent event) {
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            // A broken notification must never fail the booking that already committed.
            // Log it and move on: the client will see the truth on its next refresh.
            log.warn("Could not broadcast availability event to {}: {}", destination, e.getMessage());
        }
    }
}
