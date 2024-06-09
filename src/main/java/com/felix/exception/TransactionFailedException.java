package com.felix.exception;

import lombok.Getter;

@Getter
public class TransactionFailedException extends Exception {
    TransactionFailedErrorCode errorCode;

    public TransactionFailedException(TransactionFailedErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TransactionFailedException(TransactionFailedErrorCode errorCode) {
        this.errorCode = errorCode;
    }
}
