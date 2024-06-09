package com.felix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WithdrawalRequestModel {
    Integer id;
    int fromAccountId;
    String withdrawalId;
    String toAddress;
    BigDecimal amount;
    int status;
}
