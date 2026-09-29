package com.jewellery.erp.labels.service;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelPrintAgent;
import com.jewellery.erp.labels.entity.LabelPrintQueuePage;
import com.jewellery.erp.labels.entity.LabelSettings;
import com.jewellery.erp.labels.entity.QueuedPageStatus;
import com.jewellery.erp.labels.repository.LabelPrintAgentRepository;
import com.jewellery.erp.labels.repository.LabelPrintQueueRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queue between a hosted server and the printer in the shop.
 *
 * <p>When the application runs somewhere other than the counter, it cannot
 * spool anything itself: the printers it can see belong to a data centre. So it
 * renders each page and leaves it here, and the agent on the shop PC collects
 * the pages and puts them on the real printer.
 *
 * <p>A page is only marked printed when the agent says so. Anything it takes
 * and never reports on goes back in the queue, because a PC switched off
 * mid-run must not silently swallow a tag.
 */
@Service
public class LabelQueueService {

    private static final Logger log = LoggerFactory.getLogger(LabelQueueService.class);

    /** How long a claimed page may stay unreported before it is offered again. */
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(3);
    /** Printed pages are kept this long, then dropped - each carries a bitmap. */
    private static final Duration KEEP_PRINTED = Duration.ofDays(2);
    /** Past this, the shop PC is not listening and the screen should say so. */
    private static final Duration AGENT_ONLINE_WITHIN = Duration.ofSeconds(90);
    /** A page the agent has failed this many times is not going to print. */
    private static final int MAX_ATTEMPTS = 3;

    private final LabelPrintQueueRepository queueRepository;
    private final LabelPrintAgentRepository agentRepository;
    private final LabelPrinterService printerService;

    public LabelQueueService(
            LabelPrintQueueRepository queueRepository,
            LabelPrintAgentRepository agentRepository,
            LabelPrinterService printerService) {
        this.queueRepository = queueRepository;
        this.agentRepository = agentRepository;
        this.printerService = printerService;
    }

    // ------------------------------------------------------------ queueing ---

    /**
     * Renders the run and leaves it for the agent.
     *
     * @return how many pages were queued
     */
    @Transactional
    public int enqueue(Long jobId, List<LabelDtos.Label> labels, LabelSettings settings) {
        int across = Math.max(1, settings.getLabelsAcross());
        List<LabelPrintQueuePage> pages = new ArrayList<>();
        int pageNo = 1;
        for (int first = 0; first < labels.size(); first += across, pageNo++) {
            List<LabelDtos.Label> onPage = labels.subList(first, Math.min(first + across, labels.size()));
            byte[] png = printerService.renderPagePng(onPage, settings).orElseThrow(() ->
                    new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "inventoryItemIds",
                            "The label could not be prepared for the shop printer. Try again, "
                                    + "or check the label settings."));
            LabelPrintQueuePage page = new LabelPrintQueuePage();
            page.setJobId(jobId);
            page.setPageNo(pageNo);
            page.setImage(png);
            page.setWidthMm(LabelPrinterService.pageWidthMm(settings));
            page.setHeightMm(settings.getLabelHeightMm());
            page.setPrinterName(settings.getPrinterName());
            pages.add(page);
        }
        queueRepository.saveAll(pages);
        log.info("Queued {} page(s) for the shop print agent (job {})", pages.size(), jobId);
        return pages.size();
    }

    // -------------------------------------------------------------- agent ---

    /** The agent checking in, and telling us what printers the shop PC has. */
    @Transactional
    public LabelDtos.AgentPoll heartbeat(LabelDtos.AgentHello hello) {
        LabelPrintAgent agent = agentRepository.findById(LabelPrintAgent.SINGLETON_ID)
                .orElseGet(LabelPrintAgent::new);
        agent.setId(LabelPrintAgent.SINGLETON_ID);
        agent.setLastSeenAt(Instant.now());
        agent.setAgentVersion(hello.agentVersion());
        agent.setHostName(hello.hostName());
        agent.setPrinters(hello.printers() == null ? "" : String.join("\n", hello.printers()));
        agent.setDefaultPrinter(hello.defaultPrinter());
        agentRepository.save(agent);
        return new LabelDtos.AgentPoll(queueRepository.countByStatus(QueuedPageStatus.PENDING));
    }

    /** Hands the agent the next pages to print, oldest first. */
    @Transactional
    public List<LabelDtos.QueuedPage> claim(int max) {
        int limit = Math.clamp(max, 1, 20);
        List<LabelPrintQueuePage> pages = queueRepository.claimOldestPending(Limit.of(limit));
        Instant now = Instant.now();
        List<LabelDtos.QueuedPage> claimed = new ArrayList<>(pages.size());
        for (LabelPrintQueuePage page : pages) {
            page.setStatus(QueuedPageStatus.CLAIMED);
            page.setClaimedAt(now);
            page.setAttempts(page.getAttempts() + 1);
            claimed.add(new LabelDtos.QueuedPage(
                    page.getId(),
                    page.getPageNo(),
                    Base64.getEncoder().encodeToString(page.getImage()),
                    page.getWidthMm(),
                    page.getHeightMm(),
                    page.getPrinterName()));
        }
        return claimed;
    }

    /** What the agent says happened to a page. */
    @Transactional
    public void report(Long pageId, boolean printed, String error) {
        LabelPrintQueuePage page = queueRepository.findById(pageId).orElseThrow(() ->
                new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "pageId",
                        "No queued page with id %d.".formatted(pageId)));
        if (printed) {
            page.setStatus(QueuedPageStatus.DONE);
            page.setFinishedAt(Instant.now());
            page.setErrorMessage(null);
            return;
        }
        page.setErrorMessage(error == null ? "The agent did not say why." : error.substring(0, Math.min(500, error.length())));
        // Given up on, rather than retried for ever: a tag that will not print
        // is usually a printer that is off, and re-sending it every few seconds
        // just hides the real problem.
        boolean giveUp = page.getAttempts() >= MAX_ATTEMPTS;
        page.setStatus(giveUp ? QueuedPageStatus.FAILED : QueuedPageStatus.PENDING);
        page.setClaimedAt(null);
        if (giveUp) {
            page.setFinishedAt(Instant.now());
            log.warn("Page {} failed {} times and will not be retried: {}",
                    pageId, page.getAttempts(), page.getErrorMessage());
        }
    }

    // ------------------------------------------------------------- status ---

    /** What the settings and printing screens show about the shop PC. */
    @Transactional(readOnly = true)
    public LabelDtos.AgentStatus status() {
        Optional<LabelPrintAgent> found = agentRepository.findById(LabelPrintAgent.SINGLETON_ID);
        Instant lastSeen = found.map(LabelPrintAgent::getLastSeenAt).orElse(null);
        boolean online = lastSeen != null && lastSeen.isAfter(Instant.now().minus(AGENT_ONLINE_WITHIN));
        List<String> printers = found.map(LabelPrintAgent::getPrinters)
                .filter(s -> !s.isBlank())
                .map(s -> Arrays.stream(s.split("\n")).filter(x -> !x.isBlank()).toList())
                .orElseGet(List::of);
        return new LabelDtos.AgentStatus(
                online,
                lastSeen,
                found.map(LabelPrintAgent::getHostName).orElse(null),
                found.map(LabelPrintAgent::getAgentVersion).orElse(null),
                printers,
                found.map(LabelPrintAgent::getDefaultPrinter).orElse(null),
                queueRepository.countByStatus(QueuedPageStatus.PENDING),
                queueRepository.countByStatus(QueuedPageStatus.FAILED));
    }

    // -------------------------------------------------------- housekeeping ---

    /**
     * Puts back pages the agent took and never reported, and drops old prints.
     *
     * <p>Without the first half, closing the agent window mid-run would leave
     * those tags CLAIMED for ever and they would simply never come out.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sweep() {
        int released = queueRepository.releaseStale(Instant.now().minus(CLAIM_TIMEOUT));
        if (released > 0) {
            log.info("Returned {} unreported page(s) to the print queue", released);
        }
        queueRepository.deleteDoneBefore(Instant.now().minus(KEEP_PRINTED));
    }
}
