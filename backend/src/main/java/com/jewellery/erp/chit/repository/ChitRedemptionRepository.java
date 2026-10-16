package com.jewellery.erp.chit.repository;

import com.jewellery.erp.chit.entity.ChitRedemption;
import com.jewellery.erp.chit.entity.RedemptionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChitRedemptionRepository extends JpaRepository<ChitRedemption, Long> {

    /** What a given invoice took off chits. The chit is always wanted with it. */
    @EntityGraph(attributePaths = {"chitAccount"})
    List<ChitRedemption> findBySaleIdAndStatusOrderByIdAsc(Long saleId, RedemptionStatus status);

    /** Every redemption on an invoice, cancelled ones included, for releasing it. */
    @EntityGraph(attributePaths = {"chitAccount"})
    List<ChitRedemption> findBySaleIdOrderByIdAsc(Long saleId);
}
