package com.jewellery.erp.labels.repository;

import com.jewellery.erp.labels.entity.LabelSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelSettingsRepository extends JpaRepository<LabelSettings, Long> {}
