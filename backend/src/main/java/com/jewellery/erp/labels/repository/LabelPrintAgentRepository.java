package com.jewellery.erp.labels.repository;

import com.jewellery.erp.labels.entity.LabelPrintAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelPrintAgentRepository extends JpaRepository<LabelPrintAgent, Short> {}
