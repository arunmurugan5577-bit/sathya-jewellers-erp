package com.jewellery.erp.labels.repository;

import com.jewellery.erp.labels.entity.LabelPrintJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelPrintJobRepository extends JpaRepository<LabelPrintJob, Long> {}
