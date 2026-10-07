package com.stock.market.stock.basicinfo.observation.analysis.runner;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisConfiguration;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisProperties;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@Slf4j
public class KisStockBasicInfoAnalysisRunner implements ApplicationRunner {
    private final StockMasterBatchParsingService masterParser;
    private final KisStockBasicInfoAnalysisService service;
    private final KisStockBasicInfoAnalysisProperties properties;

    public KisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties
    ) {
        this.masterParser = Objects.requireNonNull(masterParser, "masterParser must not be null.");
        this.service = Objects.requireNonNull(service, "service must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        if (!properties.enabled()) {
            return;
        }
        var master = Objects.requireNonNull(masterParser.parseBatch(Path.of(properties.observationRoot()), properties.collectionId()),
                "Parsed master batch must not be null.");
        if (!properties.collectionId().equals(master.collection().collectionId())) {
            throw new IllegalStateException("Parsed master batch must preserve the requested collection ID.");
        }
        var result = Objects.requireNonNull(service.analyze(properties.observationId(), master), "Analysis result must not be null.");
        var type = result.screeningResult().observation().typeResolution();
        var matching = type.matchingResult();
        if (!properties.observationId().equals(result.observationId()) || !master.equals(matching.masterBatch())) {
            throw new IllegalStateException("Analysis result must preserve the requested observation ID and parsed master batch.");
        }
        log.info("Stored stock basic info analysis complete. observationId={}, collectionId={}, symbol={}, inputSha256={}, "
                        + "matchReason={}, referenceSecurityType={}, typeReason={}, restrictionStatus={}, restrictionReasons={}",
                result.observationId(), master.collection().collectionId(), result.response().requestedSymbol(), matching.apiInput().inputSha256(),
                matching.reasonCode(), type.referenceSecurityType(), type.reasonCode(),
                result.screeningResult().status(), result.screeningResult().reasonCodes());
    }

    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class)
                .web(WebApplicationType.NONE).run(args)) {
            log.info("Stock basic info manual analysis process finished.");
        }
    }
}
