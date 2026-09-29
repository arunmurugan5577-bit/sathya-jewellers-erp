package com.jewellery.erp.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} maintenance jobs - currently only the nightly
 * refresh-token purge.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
