package com.stock.market.stock.basicinfo.observation.analysis.runner;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.query.KisStockRestrictionPrecheckQueryService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisConfiguration;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisProperties;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessResult;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Objects;

@Slf4j
public class KisStockBasicInfoAnalysisRunner implements ApplicationRunner {
    private final StockMasterBatchParsingService masterParser;
    private final KisStockBasicInfoAnalysisService service;
    private final KisStockBasicInfoAnalysisProperties properties;
    private final KisStockMasterMarketWarningParser marketWarningParser;
    private final KisStockMarketWarningObservationPolicy marketWarningObservationPolicy;
    private final KisStockRestrictionAnalysisService restrictionAnalysisService;
    private final KisStockRestrictionFreshnessPolicy restrictionFreshnessPolicy;
    private final KisStockRestrictionPrecheckService restrictionPrecheckService;
    private final KisStockRestrictionPrecheckQueryService restrictionPrecheckQueryService;

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
        this(masterParser, service, properties, marketWarningParser, marketWarningObservationPolicy, restrictionAnalysisService, null);
    }

    public KisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties,
            KisStockMasterMarketWarningParser marketWarningParser,
            KisStockMarketWarningObservationPolicy marketWarningObservationPolicy,
            KisStockRestrictionAnalysisService restrictionAnalysisService,
            KisStockRestrictionFreshnessPolicy restrictionFreshnessPolicy
    ) {
        this(masterParser, service, properties, marketWarningParser, marketWarningObservationPolicy, restrictionAnalysisService,
                restrictionFreshnessPolicy, null);
    }

    public KisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties,
            KisStockMasterMarketWarningParser marketWarningParser,
            KisStockMarketWarningObservationPolicy marketWarningObservationPolicy,
            KisStockRestrictionAnalysisService restrictionAnalysisService,
            KisStockRestrictionFreshnessPolicy restrictionFreshnessPolicy,
            KisStockRestrictionPrecheckService restrictionPrecheckService
    ) {
        this(masterParser, service, properties, marketWarningParser, marketWarningObservationPolicy, restrictionAnalysisService,
                restrictionFreshnessPolicy, restrictionPrecheckService, null);
    }

    public KisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties,
            KisStockMasterMarketWarningParser marketWarningParser,
            KisStockMarketWarningObservationPolicy marketWarningObservationPolicy,
            KisStockRestrictionAnalysisService restrictionAnalysisService,
            KisStockRestrictionFreshnessPolicy restrictionFreshnessPolicy,
            KisStockRestrictionPrecheckService restrictionPrecheckService,
            KisStockRestrictionPrecheckQueryService restrictionPrecheckQueryService
    ) {
        this.masterParser = Objects.requireNonNull(masterParser, "masterParser must not be null.");
        this.service = Objects.requireNonNull(service, "service must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.marketWarningParser = marketWarningParser;
        this.marketWarningObservationPolicy = marketWarningObservationPolicy;
        this.restrictionAnalysisService = restrictionAnalysisService;
        this.restrictionFreshnessPolicy = restrictionFreshnessPolicy;
        this.restrictionPrecheckService = restrictionPrecheckService;
        this.restrictionPrecheckQueryService = restrictionPrecheckQueryService;
        if (properties.enabled() && properties.includeMarketWarnings()) {
            Objects.requireNonNull(marketWarningParser, "marketWarningParser must not be null.");
            Objects.requireNonNull(marketWarningObservationPolicy, "marketWarningObservationPolicy must not be null.");
            Objects.requireNonNull(restrictionAnalysisService, "restrictionAnalysisService must not be null.");
        }
        if (properties.enabled() && properties.checkFreshness()) {
            Objects.requireNonNull(restrictionFreshnessPolicy, "restrictionFreshnessPolicy must not be null.");
        }
        if (properties.enabled() && properties.runPrecheck()) {
            if (properties.symbol() == null) {
                Objects.requireNonNull(restrictionPrecheckService, "restrictionPrecheckService must not be null.");
            } else {
                Objects.requireNonNull(restrictionPrecheckQueryService, "restrictionPrecheckQueryService must not be null.");
            }
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
        if (properties.runPrecheck()) {
            analyzePrecheck(master, warnings);
            return;
        }
        var result = Objects.requireNonNull(restrictionAnalysisService.analyze(properties.observationId(), master, warnings),
                "Restriction analysis result must not be null.");
        var screening = result.restrictionScreeningResult();
        if (!warnings.equals(screening.marketWarningObservation())) {
            throw new IllegalStateException("Restriction analysis must preserve the prepared market warning observation.");
        }
        logBasicAnalysis(result.basicInfoAnalysis(), master);
        logRestrictionAnalysis(result);
        if (properties.checkFreshness()) {
            logFreshnessAnalysis(result);
        }
    }

    private void logRestrictionAnalysis(KisStockRestrictionAnalysisResult result) {
        var screening = result.restrictionScreeningResult();
        var warnings = screening.marketWarningObservation();
        var parsed = warnings.source();
        var source = parsed.source();
        log.info("Stored stock restriction analysis complete. observationId={}, collectionId={}, symbol={}, warningMarket={}, "
                        + "warningInputSha256={}, warningParserVersion={}, warningObservationVersion={}, combinedScreeningVersion={}, "
                        + "combinedRestrictionStatus={}, combinedRestrictionReasons={}",
                result.basicInfoAnalysis().observationId(), properties.collectionId(), result.basicInfoAnalysis().response().requestedSymbol(),
                source.market(), source.inputSha256(), parsed.parserVersion(), warnings.observationVersion(), screening.screeningVersion(),
                screening.status(), screening.reasonCodes());
    }

    private void logFreshnessAnalysis(KisStockRestrictionAnalysisResult analysis) {
        var request = freshnessRequest();
        var result = Objects.requireNonNull(restrictionFreshnessPolicy.evaluate(request, analysis), "Restriction freshness result must not be null.");
        if (!request.equals(result.request()) || !analysis.equals(result.analysisResult())) {
            throw new IllegalStateException("Restriction freshness must preserve the evaluation request and complete analysis result.");
        }
        logFreshnessResult(result);
    }

    private void logFreshnessResult(KisStockRestrictionFreshnessResult result) {
        var request = result.request();
        var analysis = result.analysisResult();
        log.info("Stored stock restriction freshness check complete. observationId={}, collectionId={}, symbol={}, evaluatedAt={}, "
                        + "maxMasterAge={}, maxBasicInfoAge={}, freshnessStatus={}, freshnessReasons={}, freshnessVersion={}",
                analysis.basicInfoAnalysis().observationId(), properties.collectionId(), analysis.basicInfoAnalysis().response().requestedSymbol(),
                request.evaluatedAt(), request.maxMasterAge(), request.maxBasicInfoAge(), result.status(), result.reasonCodes(), result.freshnessVersion());
    }

    private void analyzePrecheck(StockMasterBatchParseResult master, KisStockMarketWarningObservationResult warnings) {
        var request = freshnessRequest();
        KisStockRestrictionPrecheckResult result;
        if (properties.symbol() == null) {
            result = Objects.requireNonNull(restrictionPrecheckService.precheck(properties.observationId(), master, warnings, request),
                    "Restriction precheck result must not be null.");
        } else {
            result = Objects.requireNonNull(restrictionPrecheckQueryService.precheckLatest(properties.symbol(), master, warnings, request),
                    "Restriction precheck selection must not be null.")
                    .orElseThrow(() -> new NoSuchElementException("KIS stock basic info observation not found. symbol="
                            + properties.symbol() + ", evaluatedAt=" + request.evaluatedAt()));
        }
        var freshness = result.freshnessResult();
        var analysis = freshness.analysisResult();
        if (!request.equals(freshness.request())) {
            throw new IllegalStateException("Restriction precheck must preserve the evaluation request.");
        }
        if (!warnings.equals(analysis.restrictionScreeningResult().marketWarningObservation())) {
            throw new IllegalStateException("Restriction analysis must preserve the prepared market warning observation.");
        }
        // Reuse the service result; running analysis or freshness here again would duplicate the work.
        logBasicAnalysis(analysis.basicInfoAnalysis(), master);
        logRestrictionAnalysis(analysis);
        logFreshnessResult(freshness);
        log.info("Stored stock restriction precheck complete. observationId={}, collectionId={}, symbol={}, evaluatedAt={}, "
                        + "maxMasterAge={}, maxBasicInfoAge={}, combinedRestrictionStatus={}, combinedRestrictionReasons={}, "
                        + "freshnessStatus={}, freshnessReasons={}, precheckStatus={}, precheckVersion={}",
                analysis.basicInfoAnalysis().observationId(), properties.collectionId(), analysis.basicInfoAnalysis().response().requestedSymbol(),
                request.evaluatedAt(), request.maxMasterAge(), request.maxBasicInfoAge(), analysis.restrictionScreeningResult().status(),
                analysis.restrictionScreeningResult().reasonCodes(), freshness.status(), freshness.reasonCodes(), result.status(), result.precheckVersion());
    }

    private KisStockRestrictionFreshnessRequest freshnessRequest() {
        return new KisStockRestrictionFreshnessRequest(properties.evaluatedAt(), properties.maxMasterAge(), properties.maxBasicInfoAge());
    }

    private void logBasicAnalysis(KisStockBasicInfoAnalysisResult result, StockMasterBatchParseResult master) {
        var type = result.screeningResult().observation().typeResolution();
        var matching = type.matchingResult();
        boolean sameObservation = properties.symbol() == null ? properties.observationId().equals(result.observationId())
                : properties.symbol().equals(result.response().requestedSymbol());
        if (!sameObservation || !master.equals(matching.masterBatch())) {
            throw new IllegalStateException(properties.symbol() == null
                    ? "Analysis result must preserve the requested observation ID and parsed master batch."
                    : "Analysis result must preserve the requested symbol and parsed master batch.");
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
