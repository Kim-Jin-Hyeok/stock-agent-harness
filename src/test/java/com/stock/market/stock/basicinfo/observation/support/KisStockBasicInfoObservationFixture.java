package com.stock.market.stock.basicinfo.observation.support;

import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.stream.Stream;

public final class KisStockBasicInfoObservationFixture {
    public static final String SYMBOL = "0004Y0";
    public static final Instant START = Instant.parse("2026-10-07T01:00:00.123456789Z");
    public static final Instant END = START.plusMillis(53);
    public static final Instant RECORDED_AT = START.plusSeconds(2);

    private KisStockBasicInfoObservationFixture() {
    }

    public static byte[] content() {
        return " \n{\"rt_cd\":\"0\",\"synthetic\":\"original-payload\"}\r\n".getBytes(StandardCharsets.UTF_8);
    }

    public static KisStockBasicInfoRawResponse response() {
        return response(content());
    }

    public static KisStockBasicInfoRawResponse response(byte[] content) {
        return new KisStockBasicInfoRawResponse(SYMBOL, START, END, 200, content);
    }

    public static KisStockBasicInfoRawResponse normalized(KisStockBasicInfoRawResponse original) {
        return new KisStockBasicInfoRawResponse(original.requestedSymbol(), original.requestStartedAt().truncatedTo(ChronoUnit.MICROS),
                original.responseReceivedAt().truncatedTo(ChronoUnit.MICROS), original.httpStatus(), original.content());
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static Stream<byte[]> contents() {
        return Stream.of(content(), "{\"rt_cd\":\"1\",\"msg1\":\"Synthetic business failure\"}".getBytes(StandardCharsets.UTF_8),
                "not-json".getBytes(StandardCharsets.UTF_8), new byte[]{0, (byte) 0xff, (byte) 0xc0, 13, 10});
    }
}
