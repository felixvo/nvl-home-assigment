package com.felix.dto;

import com.felix.model.WithdrawalRequestStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WithdrawalResponseDto {
    Integer id;
    int fromAccountId;
    String withdrawalId;
    String toAddress;
    BigDecimal amount;
    WithdrawalRequestStatusEnum status;
}
