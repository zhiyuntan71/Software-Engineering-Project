package com.sc2006group5.car_one_stop.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY) // 422
public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException() {
        super("Invalid OTP code");
    }
}