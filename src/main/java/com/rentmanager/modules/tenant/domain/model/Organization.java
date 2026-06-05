package com.rentmanager.modules.tenant.domain.model;
import com.rentmanager.modules.tenant.domain.enums.OrganizationType;
import com.rentmanager.modules.tenant.domain.valueobject.OrganizationAddress;
import com.rentmanager.modules.tenant.domain.valueobject.OrganizationContact;
import com.rentmanager.domain.base.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "organizations",
        indexes = {
                @Index(
                        name = "idx_organization_tenant_id",
                        columnList = "tenant_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends BaseEntity {

 @Column(name = "tenant_id", nullable = false, updatable = false)
 private UUID tenantId;

 @Column(name = "legal_name", nullable = false, length = 150)
 private String legalName;

 @Column(name = "trading_name", length = 150)
 private String tradingName;

 @Column(name = "registration_number", length = 100)
 private String registrationNumber;

 @Column(name = "tax_number", length = 100)
 private String taxNumber;

 @Enumerated(EnumType.STRING)
 @Column(name = "organization_type", nullable = false, length = 50)
 private OrganizationType organizationType;

 @Embedded
 private OrganizationAddress address;

 @Embedded
 @AttributeOverrides({
         @AttributeOverride(
                 name = "contactName",
                 column = @Column(name = "organization_contact_name")
         ),
         @AttributeOverride(
                 name = "email",
                 column = @Column(name = "organization_contact_email")
         ),
         @AttributeOverride(
                 name = "phoneNumber",
                 column = @Column(name = "organization_contact_phone_number")
         ),
         @AttributeOverride(
                 name = "website",
                 column = @Column(name = "organization_website")
         )
 })
 private OrganizationContact contact;

 @Column(name = "verified", nullable = false)
 private boolean verified;

 private Organization(
         UUID tenantId,
         String legalName,
         OrganizationType organizationType
 ) {

  this.tenantId = tenantId;
  this.legalName = legalName;
  this.organizationType = organizationType;
  this.verified = false;
 }

 public static Organization create(
         UUID tenantId,
         String legalName,
         OrganizationType organizationType
 ) {

  return new Organization(
          tenantId,
          legalName,
          organizationType
  );
 }

 public void updateAddress(OrganizationAddress address) {

  if (address == null) {
   throw new IllegalArgumentException(
           "Organization address cannot be null"
   );
  }

  this.address = address;
 }

 public void updateContact(OrganizationContact contact) {

  if (contact == null) {
   throw new IllegalArgumentException(
           "Organization contact cannot be null"
   );
  }

  this.contact = contact;
 }

 public void verify() {

  if (this.verified) {
   throw new IllegalStateException(
           "Organization already verified"
   );
  }

  this.verified = true;
 }
}