package com.jewellery.erp.labels.repository;

import com.jewellery.erp.labels.entity.LabelPrintQueuePage;
import com.jewellery.erp.labels.entity.QueuedPageStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelPrintQueueRepository extends JpaRepository<LabelPrintQueuePage, Long> {

    /**
     * The oldest pages still waiting, locked so two agents cannot claim the same
     * tag. There should only ever be one agent, but a second one started by
     * accident must not double-print the shop's stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LabelPrintQueuePage p where p.status = 'PENDING' order by p.id")
    List<LabelPrintQueuePage> claimOldestPending(Limit limit);

    long countByStatus(QueuedPageStatus status);

    List<LabelPrintQueuePage> findByJobIdOrderByPageNo(Long jobId);

    /**
     * Returns pages the agent took but never reported on - it was closed, or the
     * PC was switched off mid-run. Without this they would sit CLAIMED for ever
     * and the tags would never print.
     */
    @Modifying
    @Query("""
            update LabelPrintQueuePage p
               set p.status = 'PENDING', p.claimedAt = null
             where p.status = 'CLAIMED' and p.claimedAt < :before""")
    int releaseStale(@Param("before") Instant before);

    /** Housekeeping: finished pages carry a bitmap each and are worth dropping. */
    @Modifying
    @Query("delete from LabelPrintQueuePage p where p.status = 'DONE' and p.finishedAt < :before")
    int deleteDoneBefore(@Param("before") Instant before);
}
