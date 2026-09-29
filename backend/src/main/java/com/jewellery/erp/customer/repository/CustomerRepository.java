package com.jewellery.erp.customer.repository;

import com.jewellery.erp.customer.entity.Customer;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    /**
     * The counter search behind the customer picker: name, mobile or code, active
     * customers only, capped by the pageable. Contains-matching, because staff
     * type whatever part of the name they remember.
     */
    @Query("""
            select c from Customer c
            where c.active = true
              and (lower(c.fullName) like lower(concat('%', :term, '%'))
                   or c.mobileNumber like concat('%', :term, '%')
                   or lower(c.customerCode) like lower(concat('%', :term, '%')))
            order by c.fullName
            """)
    List<Customer> searchActive(@Param("term") String term, Pageable pageable);

    @Query("select count(s) from Sale s where s.customer.id = :id")
    long countSales(@Param("id") Long id);

    @Query("select count(t) from OldMetalTransaction t where t.customer.id = :id")
    long countOldMetalTransactions(@Param("id") Long id);
}
