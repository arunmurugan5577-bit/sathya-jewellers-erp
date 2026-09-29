package com.jewellery.erp.customer.service;

import com.jewellery.erp.common.config.BusinessClock;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.customer.dto.CustomerDto;
import com.jewellery.erp.customer.dto.CustomerRequest;
import com.jewellery.erp.customer.dto.CustomerSummaryDto;
import com.jewellery.erp.customer.entity.Customer;
import com.jewellery.erp.customer.mapper.CustomerMapper;
import com.jewellery.erp.customer.repository.CustomerRepository;
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.DocumentSeries;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers.
 *
 * <p>The customer code is issued from the numbering service, never typed, so two
 * staff members adding customers at the same moment cannot collide. Mobile
 * number is deliberately not unique - families share phones.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final int LOOKUP_LIMIT = 20;

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final DocumentNumberService documentNumberService;
    private final BusinessClock businessClock;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerMapper customerMapper,
            DocumentNumberService documentNumberService,
            BusinessClock businessClock) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.documentNumberService = documentNumberService;
        this.businessClock = businessClock;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<CustomerDto> findAll(String search, Boolean active, Pageable pageable) {
        return PageResponse.from(customerRepository.findAll(matching(search, active), pageable), customerMapper::toDto);
    }

    public CustomerDto findById(Long id) {
        return customerMapper.toDto(requireCustomer(id));
    }

    /** Picker search. An empty term returns nothing rather than the whole customer list. */
    public List<CustomerSummaryDto> lookup(String term) {
        String trimmed = StringNormalizer.trimToNull(term);
        if (trimmed == null) {
            return List.of();
        }
        return customerRepository.searchActive(trimmed, PageRequest.of(0, LOOKUP_LIMIT)).stream()
                .map(customerMapper::toSummary)
                .toList();
    }

    /**
     * Resolves a customer for another module - a sale or a purchase bill. The
     * customer must exist and be active: a deactivated customer is someone the shop
     * decided to stop dealing with.
     */
    public Customer requireActive(Long id) {
        Customer customer = customerRepository
                .findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.CUSTOMER_NOT_FOUND, "Customer", id));
        if (!customer.isActive()) {
            throw new BusinessRuleException(ErrorCode.CUSTOMER_INACTIVE, "customerId",
                    "Customer %s is inactive and cannot be billed.".formatted(customer.getFullName()));
        }
        return customer;
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public CustomerDto create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        customer.setActive(true);
        customer.setCustomerCode(documentNumberService.next(DocumentSeries.CUSTOMER, businessClock.today()));

        Customer saved = customerRepository.save(customer);
        log.info("Customer {} created", saved.getCustomerCode());
        return customerMapper.toDto(saved);
    }

    @Transactional
    public CustomerDto update(Long id, CustomerRequest request) {
        Customer customer = requireCustomer(id);
        apply(customer, request);
        log.info("Customer {} updated", customer.getCustomerCode());
        return customerMapper.toDto(customer);
    }

    @Transactional
    public CustomerDto updateStatus(Long id, boolean active) {
        Customer customer = requireCustomer(id);
        customer.setActive(active);
        log.info("Customer {} {}", customer.getCustomerCode(), active ? "activated" : "deactivated");
        return customerMapper.toDto(customer);
    }

    /** Only a customer with no transactions may be deleted; anyone else is deactivated. */
    @Transactional
    public void delete(Long id) {
        Customer customer = requireCustomer(id);
        long references = customerRepository.countSales(id) + customerRepository.countOldMetalTransactions(id);
        if (references > 0) {
            throw ReferencedRecordException.of(
                    "Customer", customer.getFullName(), "%d sale(s) or purchase bill(s)".formatted(references));
        }
        customerRepository.delete(customer);
        log.info("Customer {} deleted", customer.getCustomerCode());
    }

    // ------------------------------------------------------------- helpers ---

    private void apply(Customer customer, CustomerRequest request) {
        customer.setFullName(StringNormalizer.normalizeName(request.fullName()));
        customer.setMobileNumber(StringNormalizer.trimToNull(request.mobileNumber()));
        customer.setEmail(StringNormalizer.normalizeLower(request.email()));
        customer.setAddressLine1(StringNormalizer.trimToNull(request.addressLine1()));
        customer.setAddressLine2(StringNormalizer.trimToNull(request.addressLine2()));
        customer.setCity(StringNormalizer.normalizeName(request.city()));
        customer.setState(StringNormalizer.normalizeName(request.state()));
        customer.setPincode(StringNormalizer.trimToNull(request.pincode()));
        customer.setGstin(StringNormalizer.normalizeCode(request.gstin()));
        customer.setPan(StringNormalizer.normalizeCode(request.pan()));
    }

    private Specification<Customer> matching(String search, Boolean active) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String term = StringNormalizer.trimToNull(search);
            if (term != null) {
                String pattern = "%" + term.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern),
                        builder.like(root.get("mobileNumber"), "%" + term + "%"),
                        builder.like(builder.lower(root.get("customerCode")), pattern)));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Customer requireCustomer(Long id) {
        return customerRepository
                .findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.CUSTOMER_NOT_FOUND, "Customer", id));
    }
}
