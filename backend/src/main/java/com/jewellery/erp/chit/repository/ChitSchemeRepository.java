package com.jewellery.erp.chit.repository;

import com.jewellery.erp.chit.entity.ChitScheme;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChitSchemeRepository extends JpaRepository<ChitScheme, Long> {

    /** What a customer can be offered, cheapest first - the order the shop quotes them in. */
    List<ChitScheme> findByActiveTrueOrderByMonthlyAmountAsc();

    Optional<ChitScheme> findByCode(String code);
}
