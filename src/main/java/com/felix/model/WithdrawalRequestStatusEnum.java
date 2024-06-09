package com.felix.model;


import lombok.Getter;

@Getter
public enum WithdrawalRequestStatusEnum {
    CREATED(0),

    REQUESTED(1),

    SUCCESS(2),

    FAILED(3),

    UNKNOWN(-1);

    private final int code;

    WithdrawalRequestStatusEnum(int code) {
        this.code = code;
    }

    public static WithdrawalRequestStatusEnum fromCode(int code) {
        switch (code) {
            case 0:
                return WithdrawalRequestStatusEnum.CREATED;
            case 1:
                return WithdrawalRequestStatusEnum.REQUESTED;
            case 2:
                return WithdrawalRequestStatusEnum.SUCCESS;
            case 3:
                return WithdrawalRequestStatusEnum.FAILED;
            default:
                return WithdrawalRequestStatusEnum.UNKNOWN;
        }
    }
}
