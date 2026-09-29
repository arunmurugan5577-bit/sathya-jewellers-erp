package com.jewellery.erp.customer.mapper;

import com.jewellery.erp.customer.dto.CustomerDto;
import com.jewellery.erp.customer.dto.CustomerSummaryDto;
import com.jewellery.erp.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerDto toDto(Customer c) {
        return new CustomerDto(
                c.getId(), c.getCustomerCode(), c.getFullName(), c.getMobileNumber(), c.getEmail(),
                c.getAddressLine1(), c.getAddressLine2(), c.getCity(), c.getState(), c.getPincode(),
                c.getGstin(), c.getPan(), c.formattedAddress(), c.isActive(),
                c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy());
    }

    public CustomerSummaryDto toSummary(Customer c) {
        return new CustomerSummaryDto(
                c.getId(), c.getCustomerCode(), c.getFullName(), c.getMobileNumber(), c.formattedAddress());
    }
}
