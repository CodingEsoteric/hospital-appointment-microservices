package edu.assignment.appointment.service;

import edu.assignment.appointment.dto.BookingRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Produces a stable, versioned fingerprint without storing the raw request body. */
public final class BookingRequestFingerprint {
    private BookingRequestFingerprint() {}

    public static String of(BookingRequest request) {
        String canonicalRequest = "booking-v1\npatientId=" + request.patientId() + "\nslotId=" + request.slotId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
