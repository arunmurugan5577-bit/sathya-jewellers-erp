package com.jewellery.erp.wholesale.repository;

import com.jewellery.erp.wholesale.entity.WholesaleBalance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WholesaleBalanceRepository extends JpaRepository<WholesaleBalance, Long> {

    /**
     * The party's account, locked.
     *
     * <p>Two estimates for the same party at once would otherwise both read the
     * same opening balance and the second would overwrite the first's closing
     * figures. The lock is taken before the invoice counter, matching the order
     * {@code SaleService} uses, so the two can never deadlock against each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WholesaleBalance b where b.customerId = :customerId")
    Optional<WholesaleBalance> lockByCustomerId(@Param("customerId") Long customerId);
}
