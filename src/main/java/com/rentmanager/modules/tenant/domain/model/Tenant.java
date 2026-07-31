package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "payout_phone_number", length = 20)
    private String payoutPhoneNumber;

    @Column(name = "address", length = 255)
    private String address;

 @Enumerated(EnumType.STRING)
 @Column(name = "status", nullable = false, length = 50)
 private TenantStatus status;

 @Enumerated(EnumType.STRING)
 @Column(name = "type", nullable = false, length = 50)
 private TenantType type;

 @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
 private BigDecimal commissionRate;

 @Enumerated(EnumType.STRING)
 @Column(name = "subscription_status", nullable = false, length = 50)
 private SubscriptionStatus subscriptionStatus;

 @Column(name = "organization_id")
 private UUID organizationId;

 @Getter
 private String clerkOrgId;

 @Column(name = "active_subscription_id")
 private UUID activeSubscriptionId;

 // ----------------------------------------------------------------
 // BILLING MODE (PHASE 1 DUAL REVENUE MODEL)
 // ----------------------------------------------------------------
 // Single source of truth for revenue treatment at rent-payment time.
 // Fail-closed: COMMISSION (pay-as-you-go) unless a premium subscription
 // has been activated. While PREMIUM_MONTHLY (including during the grace
 // window after a failed renewal), no commission is deducted from rent
 // payments - 100% net is disbursed to the landlord. See BillingMode.

 @Enumerated(EnumType.STRING)
 @Column(name = "billing_mode", nullable = false, length = 50)
 private BillingMode billingMode;

 // Active premium plan state. Only meaningful while billingMode ==
 // PREMIUM_MONTHLY; retained after a revert for record/audit.
 @Column(name = "subscription_plan_id")
 private UUID subscriptionPlanId;

 @Column(name = "plan_start_date")
 private LocalDate planStartDate;

 @Column(name = "plan_end_date")
 private LocalDate planEndDate;

 @Column(name = "plan_grace_ends_at")
 private LocalDate planGraceEndsAt;

 @Column(name = "plan_auto_renew", nullable = false)
 private boolean planAutoRenew;

 @Embedded
 @AttributeOverrides({
         @AttributeOverride(name = "logoUrl", column = @Column(name = "branding_logo_url")),
         @AttributeOverride(name = "faviconUrl", column = @Column(name = "branding_favicon_url")),
         @AttributeOverride(name = "primaryColor", column = @Column(name = "branding_primary_color")),
         @AttributeOverride(name = "secondaryColor", column = @Column(name = "branding_secondary_color"))
 })
 private BrandingSettings brandingSettings;

 // Per-landlord M-Pesa Daraja credentials. Always non-null on a hydrated
 // Tenant (see DarajaCredentials.unconfigured() used as the default in the
 // constructor below, and the equivalent fallback in
 // TenantPersistenceMapper.toDomain()) — check
 // darajaCredentials.isConfigured() rather than null-checking this field.
 private DarajaCredentials darajaCredentials;

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
  this.billingMode = BillingMode.COMMISSION;
  this.planAutoRenew = true;

  this.active = false;

  this.onboardingCompleted = false;

  this.commissionRate = new BigDecimal("0.0500"); // 5% default

  this.darajaCredentials = DarajaCredentials.unconfigured();
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

 // NOTE: assignTenant(UUID) removed as part of the broader event-publish
 // sweep (item 4.3). It collided in name and signature with
 // BaseTenantEntity.assignTenant(UUID) — the once-only, isolation-enforcing
 // method every other aggregate in this codebase relies on — while doing
 // something unrelated (setting organizationId, no guard). Tenant does not
 // extend BaseTenantEntity/AggregateRoot, so this was not yet a live
 // override, but converting Tenant to an AggregateRoot in the future
 // (e.g. to support tenant lifecycle domain events) would have silently
 // shadowed the isolation guard with this method instead.
 // assignOrganization(UUID) below already does what this method did, with
 // a null check this method lacked, and is now the one method for this
 // purpose. Only known caller (TenantCommandServiceImpl.createTenant) has
 // been updated to call assignOrganization(...) instead — TODO: verify no
 // other caller of the old assignTenant(UUID) exists elsewhere in the
 // codebase before merging this change.

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
 // ADDRESS
 // ----------------------------------------------------------------

 /**
  * Optional. Not required at onboarding time — see
  * CreateTenantCommandHandler, which calls this after Tenant.create() when
  * an address is supplied rather than requiring it up front.
  */
 public void updateAddress(String address) {
  this.address = address;
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
         boolean onboardingCompleted,
         String clerkOrgId,
         BigDecimal commissionRate,
         DarajaCredentials darajaCredentials,
          String address,
          String payoutPhoneNumber,
          BillingMode billingMode,
          UUID subscriptionPlanId,
          LocalDate planStartDate,
          LocalDate planEndDate,
          LocalDate planGraceEndsAt,
          boolean planAutoRenew
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
    tenant.clerkOrgId = clerkOrgId;
    tenant.commissionRate = commissionRate;
    tenant.darajaCredentials = darajaCredentials != null ? darajaCredentials : DarajaCredentials.unconfigured();
    tenant.address = address;
    tenant.payoutPhoneNumber = payoutPhoneNumber;
    tenant.billingMode = billingMode != null ? billingMode : BillingMode.COMMISSION;
    tenant.subscriptionPlanId = subscriptionPlanId;
    tenant.planStartDate = planStartDate;
    tenant.planEndDate = planEndDate;
    tenant.planGraceEndsAt = planGraceEndsAt;
    tenant.planAutoRenew = planAutoRenew;
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
 // PREMIUM MONTHLY BILLING LIFECYCLE (PHASE 1 DUAL REVENUE MODEL)
 // ----------------------------------------------------------------

 public boolean isPremiumBilling() {
  return BillingMode.PREMIUM_MONTHLY.equals(this.billingMode);
 }

 /**
  * Switches the landlord to PREMIUM_MONTHLY. Called on the first
  * successful subscription payment (initial activation) - the switch is
  * deliberately "pending" until money has actually moved. Also syncs
  * subscriptionStatus to ACTIVE so portal features that key off it
  * (e.g. landlordVerified) reflect the paid premium period.
  */
 public void activatePremiumSubscription(
         UUID subscriptionPlanId,
         LocalDate startDate,
         LocalDate endDate
 ) {
  if (isPremiumBilling()) {
   throw new IllegalStateException("Tenant is already on premium monthly billing");
  }
  if (subscriptionPlanId == null) {
   throw new IllegalArgumentException("Subscription plan ID cannot be null");
  }
  validatePlanDateRange(startDate, endDate);

  this.billingMode = BillingMode.PREMIUM_MONTHLY;
  this.subscriptionPlanId = subscriptionPlanId;
  this.planStartDate = startDate;
  this.planEndDate = endDate;
  this.planGraceEndsAt = null;
  this.planAutoRenew = true;
  this.subscriptionStatus = SubscriptionStatus.ACTIVE;
 }

 /**
  * Extends the paid premium period after a successful renewal payment.
  */
 public void extendPremiumSubscription(LocalDate newEndDate) {
  if (!isPremiumBilling()) {
   throw new IllegalStateException("Tenant is not on premium monthly billing");
  }
  if (newEndDate == null) {
   throw new IllegalArgumentException("New plan end date cannot be null");
  }
  if (this.planEndDate != null && newEndDate.isBefore(this.planEndDate)) {
   throw new IllegalArgumentException("New plan end date cannot be before current end date");
  }

  this.planEndDate = newEndDate;
  this.planGraceEndsAt = null;
  this.subscriptionStatus = SubscriptionStatus.ACTIVE;
 }

 /**
  * Enters the grace window after a failed/timed-out renewal payment.
  * Premium benefits (zero commission on rent payments) continue during
  * grace; the scheduler reverts to COMMISSION if grace ends unpaid.
  */
 public void enterPremiumGracePeriod(LocalDate graceEndsAt) {
  if (!isPremiumBilling()) {
   throw new IllegalStateException("Tenant is not on premium monthly billing");
  }
  if (graceEndsAt == null) {
   throw new IllegalArgumentException("Grace end date cannot be null");
  }

  this.planGraceEndsAt = graceEndsAt;
  this.subscriptionStatus = SubscriptionStatus.GRACE_PERIOD;
 }

 /**
  * Voluntary downgrade: stop auto-renewing. Reverts to COMMISSION at the
  * end of the already-paid period (no clawback), handled by the scheduler.
  * A downgrade requested during the grace window reverts immediately
  * instead - the landlord has already signalled they are not paying.
  */
 public void markPremiumNonRenewal() {
  if (!isPremiumBilling()) {
   throw new IllegalStateException("Tenant is not on premium monthly billing");
  }

  this.planAutoRenew = false;
 }

 /**
  * Reverts the landlord to COMMISSION billing. Called by the scheduler at
  * the end of a paid period (voluntary downgrade), after an unpaid grace
  * window, and on immediate cancel during grace. Never locks the landlord
  * out of core functionality - pay-as-you-go commission simply resumes.
  */
 public void revertToCommissionBilling() {
  if (!isPremiumBilling()) {
   return;
  }

  this.billingMode = BillingMode.COMMISSION;
  this.planGraceEndsAt = null;
  this.planAutoRenew = false;
  this.subscriptionStatus = SubscriptionStatus.LAPSED;
 }

 private void validatePlanDateRange(LocalDate startDate, LocalDate endDate) {
  if (startDate == null || endDate == null) {
   throw new IllegalArgumentException("Plan start and end dates cannot be null");
  }
  if (endDate.isBefore(startDate)) {
   throw new IllegalArgumentException("Plan end date cannot be before start date");
  }
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
 // DARAJA CREDENTIALS
 // ----------------------------------------------------------------

 /**
  * Sets or replaces this landlord's M-Pesa Daraja credentials. Validation
  * of individual field presence/blankness is delegated to
  * DarajaCredentials.of(...) itself, keeping this a thin pass-through
  * consistent with how updateBranding() delegates to the value object.
  */
 public void configureDarajaCredentials(
         String consumerKey,
         String consumerSecret,
         String businessShortCode,
         String passkey
 ) {
  this.darajaCredentials = DarajaCredentials.of(
          consumerKey,
          consumerSecret,
          businessShortCode,
          passkey
  );
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

 public void assignClerkOrgId(String clerkOrgId) {
  if (clerkOrgId == null || clerkOrgId.isBlank()) {
   throw new IllegalArgumentException("Clerk organization ID cannot be null or blank");
  }
  this.clerkOrgId = clerkOrgId;
 }

  public void updatePayoutPhoneNumber(String payoutPhoneNumber) {
   this.payoutPhoneNumber = payoutPhoneNumber;
  }

  public String getPayoutPhoneNumber() { return payoutPhoneNumber; }

  public void updateCommissionRate(BigDecimal commissionRate) {
  if (commissionRate == null
          || commissionRate.compareTo(BigDecimal.ZERO) < 0
          || commissionRate.compareTo(BigDecimal.ONE) > 0) {
   throw new IllegalArgumentException("Commission rate must be between 0 and 1");
  }
  this.commissionRate = commissionRate;
 }
}