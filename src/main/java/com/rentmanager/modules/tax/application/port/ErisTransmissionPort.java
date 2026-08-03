package com.rentmanager.modules.tax.application.port;

import com.rentmanager.modules.tax.application.dto.ErisMonthlyFilingSubmission;
import com.rentmanager.modules.tax.application.dto.ErisPropertyRegistrationSubmission;
import com.rentmanager.modules.tax.application.dto.TransmissionResult;

/**
 * Outbound port for KRA eRITS transmission (monthly filings and property
 * registrations). Phase 1 ships a stub returning NOT_AVAILABLE.
 */
public interface ErisTransmissionPort {

    TransmissionResult registerProperty(ErisPropertyRegistrationSubmission submission);

    TransmissionResult submitMonthlyFiling(ErisMonthlyFilingSubmission submission);
}
