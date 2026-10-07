package com.stock.market.stock.basicinfo.observation.analysis.runner;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisConfiguration;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisProperties;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
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
    private final KisStockMasterMarketWarningParser marketWarningParser;
    private final KisStockMarketWarningObservationPolicy marketWarningObservationPolicy;
    private final KisStockRestrictionAnalysisService restrictionAnalysisService;

    public KisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties
    ) {
        this(masterParser, service, properties, null, null, null);
    }

    public KisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties,
            KisStockMasterMarketWarningParser marketWarningParser,
            KisStockMarketWarningObservationPolicy marketWarningObservationPolicy,
            KisStockRestrictionAnalysisService restrictionAnalysisService
    ) {
        this.masterParser = Objects.requireNonNull(masterParser, "masterParser must not be null.");
        this.service = Objects.requireNonNull(service, "service must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.marketWarningParser = marketWarningParser;
        this.marketWarningObservationPolicy = marketWarningObservationPolicy;
        this.restrictionAnalysisService = restrictionAnalysisService;
        if (properties.enabled() && properties.includeMarketWarnings()) {
            Objects.requireNonNull(marketWarningParser, "marketWarningParser must not be null.");
            Objects.requireNonNull(marketWarningObservationPolicy, "marketWarningObservationPolicy must not be null.");
            Objects.requireNonNull(restrictionAnalysisService, "restrictionAnalysisService must not be null.");
        }
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
        if (properties.includeMarketWarnings()) {
            analyzeWithMarketWarnings(master);
        } else {
            var result = Objects.requireNonNull(service.analyze(properties.observationId(), master), "Analysis result must not be null.");
            logBasicAnalysis(result, master);
        }
    }

    private void analyzeWithMarketWarnings(StockMasterBatchParseResult master) {
        var source = master.marketResults().stream().filter(result -> result.market() == properties.warningMarket())
                .findFirst().orElseThrow();
        var parsed = Objects.requireNonNull(marketWarningParser.parse(source), "Parsed market warnings must not be null.");
        if (!source.equals(parsed.source())) {
            throw new IllegalStateException("Parsed market warnings must preserve the selected master source.");
        }
        var warnings = Objects.requireNonNull(marketWarningObservationPolicy.evaluate(parsed), "Market warning observation must not be null.");
        if (!parsed.equals(warnings.source())) {
            throw new IllegalStateException("Market warning observation must preserve the parsed warning source.");
        }
        var result = Objects.requireNonNull(restrictionAnalysisService.analyze(properties.observationId(), master, warnings),
                "Restriction analysis result must not be null.");
        var screening = result.restrictionScreeningResult();
        if (!warnings.equals(screening.marketWarningObservation())) {
            throw new IllegalStateException("Restriction analysis must preserve the prepared market warning observation.");
        }
        logBasicAnalysis(result.basicInfoAnalysis(), master);
        log.info("Stored stock restriction analysis complete. observationId={}, collectionId={}, symbol={}, warningMarket={}, "
                        + "warningInputSha256={}, warningParserVersion={}, warningObservationVersion={}, combinedScreeningVersion={}, "
                        + "combinedRestrictionStatus={}, combinedRestrictionReasons={}",
                result.basicInfoAnalysis().observationId(), master.collection().collectionId(), result.basicInfoAnalysis().response().requestedSymbol(),
                source.market(), source.inputSha256(), parsed.parserVersion(), warnings.observationVersion(), screening.screeningVersion(),
                screening.status(), screening.reasonCodes());
    }

    private void logBasicAnalysis(KisStockBasicInfoAnalysisResult result, StockMasterBatchParseResult master) {
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
