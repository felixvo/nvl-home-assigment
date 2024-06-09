package com.felix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLogModel {
    int id;
    int accountId;
    BigDecimal amount;
    String details;
    TransactionLogType type;
}
