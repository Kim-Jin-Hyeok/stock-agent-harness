package com.stock.broker.kis.order.cancellation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.broker.order.BrokerOrderReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KisOrderCancellationRequestTest {

    @Test
    void createsAllRemainingCancellationWithUppercaseKisFields()
            throws Exception {
        KisOrderCancellationRequest request =
                KisOrderCancellationRequest.allRemaining(
                        "12345678",
                        "01",
                        new BrokerOrderReference("0000123456", "06010"),
                        "00",
                        7L,
                        70_000L,
                        "KRX"
                );

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).isEqualTo(
                "{\"CANO\":\"12345678\","
                        + "\"ACNT_PRDT_CD\":\"01\","
                        + "\"KRX_FWDG_ORD_ORGNO\":\"06010\","
                        + "\"ORGN_ODNO\":\"0000123456\","
                        + "\"ORD_DVSN\":\"00\","
                        + "\"RVSE_CNCL_DVSN_CD\":\"02\","
                        + "\"ORD_QTY\":\"7\","
                        + "\"ORD_UNPR\":\"70000\","
                        + "\"QTY_ALL_ORD_YN\":\"Y\","
                        + "\"EXCG_ID_DVSN_CD\":\"KRX\","
                        + "\"CNDT_PRIC\":\"\"}"
        );
    }

    @Test
    void rejectsMissingOriginalOrderOrganizationNumber() {
        BrokerOrderReference reference =
                new BrokerOrderReference("0000123456", null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> KisOrderCancellationRequest.allRemaining(
                        "12345678",
                        "01",
                        reference,
                        "00",
                        7L,
                        70_000L,
                        "KRX"
                ))
                .withMessage(
                        "originalReference.organizationNumber "
                                + "must not be blank."
                );
    }

    @Test
    void rejectsNonPositiveCancelableQuantity() {
        BrokerOrderReference reference =
                new BrokerOrderReference("0000123456", "06010");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> KisOrderCancellationRequest.allRemaining(
                        "12345678",
                        "01",
                        reference,
                        "00",
                        0L,
                        70_000L,
                        "KRX"
                ))
                .withMessage("cancelableQuantity must be positive.");
    }
}
