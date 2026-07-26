package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.SignInTokenResult;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendSignInLinkService {

    private static final int SIGN_IN_TOKEN_EXPIRY_SECONDS = 604800; // 7 days

    private final ReservationRepository reservationRepository;
    private final ClerkService clerkService;
    private final SmsService smsService;

    /**
     * Re-issues a sign-in token for the reservation's Clerk user.
     * The caller must provide the reservation's phone number as
     * a simple verification that they know both the reservation ID
     * and the contact phone.
     *
     * @return true if the link was sent; false if the reservation
     *         is not in a completed state or the phone doesn't match.
     */
    public boolean resend(UUID reservationId, String phone) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("Resend requested for non-existent reservation. reservationId={}", reservationId);
            return false;
        }

        if (reservation.getClerkUserId() == null || reservation.getClerkUserId().isBlank()) {
            log.warn("Resend requested but reservation has no clerkUserId yet. reservationId={}, status={}",
                    reservationId, reservation.getStatus());
            return false;
        }

        if (!phone.trim().equals(reservation.getPhone())) {
            log.warn("Resend phone mismatch. reservationId={}", reservationId);
            return false;
        }

        SignInTokenResult tokenResult = clerkService.createSignInToken(
                reservation.getClerkUserId(),
                SIGN_IN_TOKEN_EXPIRY_SECONDS
        );

        log.info("Sign-in link URL: {}", tokenResult.url());

        smsService.sendSignInLink(reservation.getPhone(), tokenResult.url());

        log.info("Sign-in link resent. reservationId={}, clerkUserId={}",
                reservationId, reservation.getClerkUserId());

        return true;
    }
}
