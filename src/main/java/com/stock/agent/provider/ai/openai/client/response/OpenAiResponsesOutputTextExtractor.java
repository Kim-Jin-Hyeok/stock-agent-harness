package com.stock.agent.provider.ai.openai.client.response;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class OpenAiResponsesOutputTextExtractor {
    private static final String COMPLETED_STATUS = "completed";
    private static final String INCOMPLETE_STATUS = "incomplete";
    private static final String MESSAGE_OUTPUT_TYPE = "message";
    private static final String OUTPUT_TEXT_CONTENT_TYPE = "output_text";
    private static final String REFUSAL_CONTENT_TYPE = "refusal";

    public String extract(OpenAiResponsesResponse response) {
        Objects.requireNonNull(response, "response must not be null.");
        validateStatus(response);
        rejectRefusal(response);

        List<OpenAiResponsesResponse.Content> outputTexts =
                responseContents(response)
                        .filter(content -> OUTPUT_TEXT_CONTENT_TYPE.equals(
                                content.type()
                        ))
                        .toList();
        if (outputTexts.size() != 1) {
            throw new IllegalStateException(
                    "OpenAI response must contain exactly one output_text. "
                            + "count="
                            + outputTexts.size()
            );
        }

        String outputText = outputTexts.getFirst().text();
        if (outputText == null || outputText.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI output_text must not be blank."
            );
        }
        return outputText;
    }

    private void validateStatus(OpenAiResponsesResponse response) {
        if (INCOMPLETE_STATUS.equals(response.status())) {
            String reason = response.incompleteDetails() == null
                    ? null
                    : response.incompleteDetails().reason();
            throw new IllegalStateException(
                    "OpenAI response was incomplete. reason="
                            + valueOrUnknown(reason)
            );
        }
        if (!COMPLETED_STATUS.equals(response.status())) {
            throw new IllegalStateException(
                    "OpenAI response was not completed. status="
                            + valueOrUnknown(response.status())
            );
        }
    }

    private void rejectRefusal(OpenAiResponsesResponse response) {
        responseContents(response)
                .filter(content -> REFUSAL_CONTENT_TYPE.equals(
                        content.type()
                ))
                .findFirst()
                .ifPresent(content -> {
                    throw new IllegalStateException(
                            "OpenAI response was refused. reason="
                                    + valueOrUnknown(content.refusal())
                    );
                });
    }

    private Stream<OpenAiResponsesResponse.Content> responseContents(
            OpenAiResponsesResponse response
    ) {
        return response.output().stream()
                .filter(output -> MESSAGE_OUTPUT_TYPE.equals(output.type()))
                .flatMap(output -> output.content().stream());
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
