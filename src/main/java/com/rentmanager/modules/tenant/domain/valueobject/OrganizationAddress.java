package com.rentmanager.modules.tenant.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrganizationAddress {

 @Column(name = "country", length = 100)
 private String country;

 @Column(name = "state", length = 100)
 private String state;

 @Column(name = "city", length = 100)
 private String city;

 @Column(name = "postal_code", length = 50)
 private String postalCode;

 @Column(name = "address_line_1", length = 255)
 private String addressLine1;

 @Column(name = "address_line_2", length = 255)
 private String addressLine2;

 // --------------------------------------------------
 // FACTORY (SAFE CONSTRUCTION)
 // --------------------------------------------------

 public static OrganizationAddress create(
         String country,
         String state,
         String city,
         String postalCode,
         String addressLine1,
         String addressLine2
 ) {

  validate(country, "Country");
  validate(city, "City");
  validate(addressLine1, "Address line 1");

  return OrganizationAddress.builder()
          .country(country.trim())
          .state(state != null ? state.trim() : null)
          .city(city.trim())
          .postalCode(postalCode != null ? postalCode.trim() : null)
          .addressLine1(addressLine1.trim())
          .addressLine2(addressLine2 != null ? addressLine2.trim() : null)
          .build();
 }

 // --------------------------------------------------
 // VALIDATION
 // --------------------------------------------------

 private static void validate(String value, String fieldName) {

  if (value == null || value.isBlank()) {
   throw new IllegalArgumentException(
           fieldName + " is required"
   );
  }
 }
}