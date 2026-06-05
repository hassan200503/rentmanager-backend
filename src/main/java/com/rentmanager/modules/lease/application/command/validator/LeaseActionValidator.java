package com.rentmanager.modules.lease.application.command.validator;

import com.rentmanager.contract.lease.request.LeaseActionRequest;
import com.rentmanager.contract.lease.request.LeaseActionType;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LeaseActionValidator {

    public void validate(LeaseActionRequest request) {

        if (request == null) {
            throw new BusinessException(
                    "Lease action request cannot be null",
                    ErrorCode.VALIDATION_ERROR
            );
        }



        List<String> errors = new ArrayList<>();


        if (request.getPerformedBy() == null) {
            errors.add("PerformedBy is required");
        }

        if (request.getAction() == null) {
            errors.add("Action is required");
        }

        // ACTION-SPECIFIC RULES
        if (request.getAction() == LeaseActionType.ACTIVATE) {

            if (request.getActionDate() == null) {
                errors.add("Activation date is required");
            }
        }

        if (request.getAction() == LeaseActionType.TERMINATE) {

            if (request.getReason() == null || request.getReason().isBlank()) {
                errors.add("Termination reason is required");
            }
        }

        if (request.getAction() == LeaseActionType.RENEW) {

            if (request.getActionDate() == null) {
                errors.add("New end date is required for renewal");
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(
                    "Lease validation failed",
                    ErrorCode.VALIDATION_ERROR
            );
        }
    }
}