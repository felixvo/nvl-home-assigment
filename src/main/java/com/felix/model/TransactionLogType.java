package com.felix.model;

import lombok.Getter;

@Getter
public enum TransactionLogType {
    TRANSER(0),
    DEPOSIT(1),
    WITHDRAW(2);

    private int code;

    TransactionLogType(int code) {
        this.code = code;
    }

    public static TransactionLogType fromCode(int code) {
        for (TransactionLogType type : TransactionLogType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
