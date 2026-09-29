package com.jewellery.erp.hsn.service;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.hsn.dto.HsnCodeDto;
import com.jewellery.erp.hsn.dto.HsnCodeRequest;
import com.jewellery.erp.hsn.entity.HsnCode;
import com.jewellery.erp.hsn.mapper.HsnCodeMapper;
import com.jewellery.erp.hsn.repository.HsnCodeRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HSN code master.
 *
 * <p>An HSN code carries the GST rate that will be applied when the piece is
 * billed, so the rate is stored as an exact decimal and the code is unique.
 * Changing the rate on an existing code is allowed - GST rates do change - and
 * historical invoices will keep their own recorded rate once the billing module
 * exists, rather than reading it back from here.
 */
@Service
@Transactional(readOnly = true)
public class HsnCodeService {

    private static final Logger log = LoggerFactory.getLogger(HsnCodeService.class);

    private final HsnCodeRepository hsnCodeRepository;
    private final HsnCodeMapper hsnCodeMapper;

    public HsnCodeService(HsnCodeRepository hsnCodeRepository, HsnCodeMapper hsnCodeMapper) {
        this.hsnCodeRepository = hsnCodeRepository;
        this.hsnCodeMapper = hsnCodeMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<HsnCodeDto> findAll(MasterFilter filter, Pageable pageable) {
        return PageResponse.from(hsnCodeRepository.findAll(matching(filter), pageable), hsnCodeMapper::toDto);
    }

    public HsnCodeDto findById(Long id) {
        return hsnCodeMapper.toDto(requireHsnCode(id));
    }

    public List<LookupDto> findActiveLookup() {
        return hsnCodeRepository.findByActiveTrueOrderByHsnCodeAsc().stream()
                .map(hsnCodeMapper::toLookup)
                .toList();
    }

    /** Resolves a reference for another module; the record must exist and be active. */
    public HsnCode requireActive(Long id) {
        HsnCode hsnCode = requireHsnCode(id);
        if (!hsnCode.isActive()) {
            throw new BusinessRuleException(
                    "hsnId", "HSN code '%s' is inactive and cannot be used.".formatted(hsnCode.getHsnCode()));
        }
        return hsnCode;
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public HsnCodeDto create(HsnCodeRequest request) {
        String code = StringNormalizer.trimToNull(request.hsnCode());

        if (hsnCodeRepository.existsByHsnCode(code)) {
            throw new DuplicateResourceException("hsnCode", "HSN code '%s' already exists.".formatted(code));
        }

        HsnCode entity = new HsnCode();
        entity.setHsnCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        entity.setGstPercentage(request.gstPercentage());
        entity.setActive(request.active() == null || request.active());

        HsnCode saved = hsnCodeRepository.save(entity);
        log.info("HSN code '{}' created at {}%", saved.getHsnCode(), saved.getGstPercentage());
        return hsnCodeMapper.toDto(saved);
    }

    @Transactional
    public HsnCodeDto update(Long id, HsnCodeRequest request) {
        HsnCode entity = requireHsnCode(id);
        String code = StringNormalizer.trimToNull(request.hsnCode());

        if (hsnCodeRepository.existsByHsnCodeAndIdNot(code, id)) {
            throw new DuplicateResourceException("hsnCode", "HSN code '%s' already exists.".formatted(code));
        }

        entity.setHsnCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        entity.setGstPercentage(request.gstPercentage());
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        log.info("HSN code '{}' updated to {}%", entity.getHsnCode(), entity.getGstPercentage());
        return hsnCodeMapper.toDto(entity);
    }

    @Transactional
    public HsnCodeDto updateStatus(Long id, boolean active) {
        HsnCode entity = requireHsnCode(id);
        entity.setActive(active);
        log.info("HSN code '{}' {}", entity.getHsnCode(), active ? "activated" : "deactivated");
        return hsnCodeMapper.toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        HsnCode entity = requireHsnCode(id);

        long items = hsnCodeRepository.countInventoryItems(id);
        if (items > 0) {
            throw ReferencedRecordException.of(
                    "HSN code", entity.getHsnCode(), "%d inventory item(s)".formatted(items));
        }

        hsnCodeRepository.delete(entity);
        log.info("HSN code '{}' deleted", entity.getHsnCode());
    }

    // ------------------------------------------------------------- helpers ---

    private Specification<HsnCode> matching(MasterFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("hsnCode")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private HsnCode requireHsnCode(Long id) {
        return hsnCodeRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("HSN code", id));
    }
}
