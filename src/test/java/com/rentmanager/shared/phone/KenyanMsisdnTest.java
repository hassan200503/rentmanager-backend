package com.rentmanager.shared.phone;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KenyanMsisdnTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0712345678", "+254712345678", "254712345678",
            // The 01XX range — a renter on one of these could not pay before.
            "0112345678", "+254112345678", "254112345678", "0101234567"
    })
    void acceptsBothKenyanMobileRanges(String input) {
        assertThat(input).matches(KenyanMsisdn.PATTERN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "071234567", "07123456789", "0212345678", "0612345678", "+255712345678",
            "0122345678", "abc", "", "+2547123456 78"
    })
    void rejectsMalformedNumbers(String input) {
        assertThat(input).doesNotMatch(KenyanMsisdn.PATTERN);
    }

    @ParameterizedTest
    @CsvSource({
            "0712345678,+254712345678",
            "0112345678,+254112345678",
            "254112345678,+254112345678",
            "+254712345678,+254712345678",
            "' 0712345678 ',+254712345678"
    })
    void normalisesToE164(String input, String expected) {
        assertThat(KenyanMsisdn.toE164(input)).isEqualTo(expected);
        assertThat(KenyanMsisdn.toE164(input)).matches(KenyanMsisdn.E164_PATTERN);
    }
}
