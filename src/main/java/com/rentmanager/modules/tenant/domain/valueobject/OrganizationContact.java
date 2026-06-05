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
public class OrganizationContact {

 @Column(name = "contact_name", length = 150)
 private String contactName;

 @Column(name = "contact_email", length = 150)
 private String email;

 @Column(name = "contact_phone_number", length = 50)
 private String phoneNumber;

 @Column(name = "website", length = 255)
 private String website;

 // --------------------------------------------------
 // SAFE FACTORY
 // --------------------------------------------------

 public static OrganizationContact create(
         String contactName,
         String email,
         String phoneNumber,
         String website
 ) {

  validateName(contactName);
  validateEmail(email);

  return OrganizationContact.builder()
          .contactName(contactName.trim())
          .email(email.trim().toLowerCase())
          .phoneNumber(normalize(phoneNumber))
          .website(normalize(website))
          .build();
 }

 // --------------------------------------------------
 // VALIDATION
 // --------------------------------------------------

 private static void validateName(String name) {

  if (name == null || name.isBlank()) {
   throw new IllegalArgumentException(
           "Contact name is required"
   );
  }
 }

 private static void validateEmail(String email) {

  if (email == null || email.isBlank()) {
   throw new IllegalArgumentException(
           "Contact email is required"
   );
  }

  if (!email.contains("@")) {
   throw new IllegalArgumentException(
           "Invalid email format"
   );
  }
 }

 // --------------------------------------------------
 // NORMALIZATION
 // --------------------------------------------------

 private static String normalize(String value) {

  if (value == null || value.isBlank()) {
   return null;
  }

  return value.trim();
 }
}