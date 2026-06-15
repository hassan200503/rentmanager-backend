package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tenants",
        indexes = {
                @Index(name = "idx_tenant_slug", columnList = "slug"),
                @Index(name = "idx_tenant_code", columnList = "tenant_code"),
                @Index(name = "idx_tenant_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseEntity {

 @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
 private String tenantCode;

 @Column(name = "name", nullable = false, length = 150)
 private String name;

 @Column(name = "slug", nullable = false, unique = true, length = 120)
 private String slug;

 @Column(name = "email", nullable = false, length = 150)
 private String email;

 @Column(name = "phone_number", length = 50)
 private String phoneNumber;

 @Enumerated(EnumType.STRING)
 @Column(name = "status", nullable = false, length = 50)
 private TenantStatus status;

 @Enumerated(EnumType.STRING)
 @Column(name = "type", nullable = false, length = 50)
 private TenantType type;

 @Enumerated(EnumType.STRING)
 @Column(name = "subscription_status", nullable = false, length = 50)
 private SubscriptionStatus subscriptionStatus;

 @Column(name = "organization_id")
 private UUID organizationId;

 @Column(name = "active_subscription_id")
 private UUID activeSubscriptionId;

 @Embedded
 @AttributeOverrides({
         @AttributeOverride(name = "logoUrl", column = @Column(name = "branding_logo_url")),
         @AttributeOverride(name = "faviconUrl", column = @Column(name = "branding_favicon_url")),
         @AttributeOverride(name = "primaryColor", column = @Column(name = "branding_primary_color")),
         @AttributeOverride(name = "secondaryColor", column = @Column(name = "branding_secondary_color"))
 })
 private BrandingSettings brandingSettings;

 @Column(name = "timezone", length = 100)
 private String timezone;

 @Column(name = "currency", length = 20)
 private String currency;

 @Column(name = "locale", length = 20)
 private String locale;

 @Column(name = "active", nullable = false)
 private boolean active;

 @Column(name = "onboarding_completed", nullable = false)
 private boolean onboardingCompleted;

 // ----------------------------------------------------------------
 // CONSTRUCTOR
 // ----------------------------------------------------------------

 private Tenant(
         String tenantCode,
         String name,
         String slug,
         String email,
         String phoneNumber,
         TenantType type
 ) {

  validateTenantCode(tenantCode);
  validateName(name);
  validateSlug(slug);
  validateEmail(email);
  validateTenantType(type);

  this.tenantCode = tenantCode;
  this.name = name;
  this.slug = slug;
  this.email = email;
  this.phoneNumber = phoneNumber;
  this.type = type;

  this.status = TenantStatus.PENDING;
  this.subscriptionStatus = SubscriptionStatus.TRIAL;

  this.active = false;


  this.onboardingCompleted = false;
 }

 // ----------------------------------------------------------------
 // FACTORY METHODS
 // ----------------------------------------------------------------

 public static Tenant create(
         String tenantCode,
         String name,
         String slug,
         String email,
         String phoneNumber,
         TenantType type
 ) {
  return new Tenant(
          tenantCode,
          name,
          slug,
          email,
          phoneNumber,
          type
  );
 }

 public static Tenant create(
         String tenantCode,
         String name,
         String slug,
         String email,
         String phoneNumber,
         TenantType type,
         SubscriptionStatus subscriptionStatus
 ) {

  Tenant tenant = new Tenant(
          tenantCode,
          name,
          slug,
          email,
          phoneNumber,
          type
  );

  tenant.subscriptionStatus = subscriptionStatus;
  return tenant;
 }

 // ----------------------------------------------------------------
 // LIFECYCLE OPERATIONS (FIXED CONSISTENCY)
 // ----------------------------------------------------------------

    public void activate() {

  if (this.status == TenantStatus.DEACTIVATED) {
   throw new IllegalStateException("Deactivated tenant cannot be reactivated");
  }

  if (this.status == TenantStatus.ACTIVE) {
   return;
  }

  this.status = TenantStatus.ACTIVE;
  this.active = true;
 }

 public void suspend() {

  if (this.status == TenantStatus.DEACTIVATED) {
   throw new IllegalStateException("Deactivated tenant cannot be suspended");
  }

  if (this.status == TenantStatus.SUSPENDED) {
   return;
  }

  this.status = TenantStatus.SUSPENDED;
  this.active = false;
 }

 public void deactivate() {

  if (this.status == TenantStatus.DEACTIVATED) {
   return;
  }

  this.status = TenantStatus.DEACTIVATED;
  this.active = false;
 }

 // ----------------------------------------------------------------
 // SERVICE COMPATIBILITY METHODS
 // ----------------------------------------------------------------

 public void assignTenant(UUID tenantId) {

  if (tenantId == null) {
   throw new IllegalArgumentException("Tenant ID cannot be null");
  }

  this.organizationId = tenantId;
 }

 public void updateStatus(TenantStatus status) {

  if (status == null) {
   throw new IllegalArgumentException("Tenant status cannot be null");
  }

  if (this.status == TenantStatus.DEACTIVATED) {
   throw new IllegalStateException("Cannot update status of deactivated tenant");
  }

  this.status = status;

  this.active = (status == TenantStatus.ACTIVE);
 }

 public void assignOwner(String name, String email, String phoneNumber, String identifier) {

  if (name == null || name.isBlank()) {
   throw new IllegalArgumentException("Owner name is required");
  }

  if (email == null || email.isBlank()) {
   throw new IllegalArgumentException("Owner email is required");
  }

  this.email = email;
  this.phoneNumber = phoneNumber;
 }

 public void changeOwner(String name, String email, String phoneNumber, String identifier) {
  assignOwner(name, email, phoneNumber, identifier);
 }

 public void updateSubscription(SubscriptionStatus subscriptionStatus) {
  updateSubscriptionStatus(subscriptionStatus);
 }

 // ----------------------------------------------------------------
 // ONBOARDING
 // ----------------------------------------------------------------

 public void completeOnboarding() {

  if (this.onboardingCompleted) {
   return;
  }

  this.onboardingCompleted = true;
 }

 public static Tenant rehydrate(
         UUID id,
         Long version,
         String tenantCode,
         String name,
         String slug,
         String email,
         String phoneNumber,
         TenantType type,
         TenantStatus status,
         SubscriptionStatus subscriptionStatus,
         UUID organizationId,
         UUID activeSubscriptionId,
         String timezone,
         String currency,
         String locale,
         BrandingSettings brandingSettings,
         boolean active,
         boolean onboardingCompleted
 ) {
  Tenant tenant = new Tenant();

  tenant.setId(id);
  tenant.setVersion(version);
  tenant.tenantCode = tenantCode;
  tenant.name = name;
  tenant.slug = slug;
  tenant.email = email;
  tenant.phoneNumber = phoneNumber;

  tenant.type = type;
  tenant.status = status;
  tenant.subscriptionStatus = subscriptionStatus;

  tenant.organizationId = organizationId;
  tenant.activeSubscriptionId = activeSubscriptionId;

  tenant.timezone = timezone;
  tenant.currency = currency;
  tenant.locale = locale;
  tenant.brandingSettings = brandingSettings;
  tenant.active = active;
  tenant.onboardingCompleted = onboardingCompleted;

  return tenant;
 }

 // ----------------------------------------------------------------
 // ORGANIZATION
 // ----------------------------------------------------------------

 public void assignOrganization(UUID organizationId) {

  if (organizationId == null) {
   throw new IllegalArgumentException("Organization ID cannot be null");
  }

  this.organizationId = organizationId;
 }

 // ----------------------------------------------------------------
 // SUBSCRIPTION
 // ----------------------------------------------------------------

 public void assignSubscription(UUID subscriptionId) {

  if (subscriptionId == null) {
   throw new IllegalArgumentException("Subscription ID cannot be null");
  }

  this.activeSubscriptionId = subscriptionId;
 }

 public void updateSubscriptionStatus(SubscriptionStatus subscriptionStatus) {

  if (subscriptionStatus == null) {
   throw new IllegalArgumentException("Subscription status cannot be null");
  }

  this.subscriptionStatus = subscriptionStatus;
 }

 // ----------------------------------------------------------------
 // BRANDING
 // ----------------------------------------------------------------

 public void updateBranding(BrandingSettings brandingSettings) {

  if (brandingSettings == null) {
   throw new IllegalArgumentException("Branding settings cannot be null");
  }

  this.brandingSettings = brandingSettings;
 }

 // ----------------------------------------------------------------
 // LOCALIZATION
 // ----------------------------------------------------------------

 public void updateLocalization(String timezone, String currency, String locale) {
  this.timezone = timezone;
  this.currency = currency;
  this.locale = locale;
 }

 // ----------------------------------------------------------------
 // VALIDATION
 // ----------------------------------------------------------------

 private void validateTenantCode(String tenantCode) {
  if (tenantCode == null || tenantCode.isBlank()) {
   throw new IllegalArgumentException("Tenant code is required");
  }
 }

 private void validateName(String name) {
  if (name == null || name.isBlank()) {
   throw new IllegalArgumentException("Tenant name is required");
  }
 }

 private void validateSlug(String slug) {
  if (slug == null || slug.isBlank()) {
   throw new IllegalArgumentException("Tenant slug is required");
  }
 }

 private void validateEmail(String email) {
  if (email == null || email.isBlank()) {
   throw new IllegalArgumentException("Tenant email is required");
  }
 }

 private void validateTenantType(TenantType type) {
  if (type == null) {
   throw new IllegalArgumentException("Tenant type is required");
  }
 }




}