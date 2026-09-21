package com.rentmanager.modules.tenant.renter.api;

import com.rentmanager.shared.phone.KenyanMsisdn;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A renter the landlord is recording. No organisation id: the landlord comes
 * from the verified token.
 *
 * Email is optional on purpose — many renters give only a phone number, and
 * requiring an address nobody has would push landlords into inventing one.
 * When it is present it is also what later links the record to the renter's
 * own account (RenterIdentityLinker).
 */
public record AddRenterRequest(
        @NotBlank(message = "The renter's name is required")
        @Size(max = 255)
        String fullName,

        @NotBlank(message = "A phone number is required")
        @Pattern(regexp = KenyanMsisdn.PATTERN, message = KenyanMsisdn.MESSAGE)
        String phone,

        @Email(message = "Enter a valid email address, or leave it blank")
        @Size(max = 255)
        String email,

        @Size(max = 50)
        String nationalId
) {
}
