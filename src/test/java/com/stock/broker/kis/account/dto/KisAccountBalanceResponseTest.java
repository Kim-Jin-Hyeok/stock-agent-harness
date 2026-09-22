package com.stock.broker.kis.account.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.broker.account.BrokerAccountPosition;
import com.stock.broker.account.BrokerAccountSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class KisAccountBalanceResponseTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    void deserializesResponseAndMapsAccountSnapshot() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisAccountBalanceResponse response = objectMapper.readValue(
                """
                        {
                          "rt_cd": "0",
                          "msg_cd": "KIOK0560",
                          "msg1": "Request completed successfully.",
                          "output1": [
                            {
                              "pdno": "005930",
                              "hldg_qty": "10",
                              "pchs_avg_pric": "70000.0000",
                              "evlu_amt": "720000"
                            },
                            {
                              "pdno": "000660",
                              "hldg_qty": "2",
                              "pchs_avg_pric": "180000.0000",
                              "evlu_amt": "370000"
                            }
                          ],
                          "output2": [
                            {
                              "dnca_tot_amt": "8910000",
                              "tot_evlu_amt": "10000000"
                            }
                          ],
                          "ctx_area_fk100": "next-fk",
                          "ctx_area_nk100": "next-nk"
                        }
                        """,
                KisAccountBalanceResponse.class
        );

        BrokerAccountSnapshot snapshot = response.toSnapshot(OBSERVED_AT);

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.contextAreaFk100()).isEqualTo("next-fk");
        assertThat(response.contextAreaNk100()).isEqualTo("next-nk");
        assertThat(snapshot.depositAmountKrw()).isEqualTo(8_910_000L);
        assertThat(snapshot.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(snapshot.positions()).containsExactly(
                new BrokerAccountPosition("005930", 10L, 70_000L, 720_000L),
                new BrokerAccountPosition("000660", 2L, 180_000L, 370_000L)
        );
    }

    @Test
    void mapsEmptyPositionList() {
        KisAccountBalanceResponse response = successfulResponse(
                List.of(),
                List.of(new KisAccountBalanceSummaryOutput("10000000", "10000000"))
        );

        BrokerAccountSnapshot snapshot = response.toSnapshot(OBSERVED_AT);

        assertThat(snapshot.positions()).isEmpty();
    }

    @Test
    void rejectsFailedResponse() {
        KisAccountBalanceResponse response = new KisAccountBalanceResponse(
                "1",
                "EGW00123",
                "Request failed.",
                List.of(),
                List.of(),
                "",
                ""
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> response.toSnapshot(OBSERVED_AT))
                .withMessage(
                        "KIS account balance response was not successful. "
                                + "messageCode=EGW00123"
                );
    }

    @Test
    void rejectsMissingAccountSummary() {
        KisAccountBalanceResponse response = successfulResponse(
                List.of(),
                List.of()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> response.toSnapshot(OBSERVED_AT))
                .withMessage("output2 must contain account summary.");
    }

    @Test
    void rejectsInvalidNumericValue() {
        KisAccountBalanceResponse response = successfulResponse(
                List.of(new KisAccountBalancePositionOutput(
                        "005930",
                        "invalid",
                        "70000",
                        "700000"
                )),
                List.of(new KisAccountBalanceSummaryOutput("9300000", "10000000"))
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> response.toSnapshot(OBSERVED_AT))
                .withMessage("holdingQuantity must be an integer number.");
    }

    private KisAccountBalanceResponse successfulResponse(
            List<KisAccountBalancePositionOutput> positions,
            List<KisAccountBalanceSummaryOutput> summaries
    ) {
        return new KisAccountBalanceResponse(
                "0",
                "KIOK0560",
                "Request completed successfully.",
                positions,
                summaries,
                "",
                ""
        );
    }
}
