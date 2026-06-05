package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tenant_settings",
        indexes = {
                @Index(
                        name = "idx_tenant_settings_tenant_id",
                        columnList = "tenant_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantSettings extends BaseEntity {

 @Column(name = "tenant_id", nullable = false, unique = true, updatable = false)
 private UUID tenantId;

 @Column(name = "timezone", nullable = false, length = 100)
 private String timezone;

 @Column(name = "currency", nullable = false, length = 20)
 private String currency;

 @Column(name = "locale", nullable = false, length = 20)
 private String locale;

 @Column(name = "email_notifications_enabled", nullable = false)
 private boolean emailNotificationsEnabled;

 @Column(name = "sms_notifications_enabled", nullable = false)
 private boolean smsNotificationsEnabled;

 @Column(name = "push_notifications_enabled", nullable = false)
 private boolean pushNotificationsEnabled;

 @Column(name = "maintenance_module_enabled", nullable = false)
 private boolean maintenanceModuleEnabled;

 @Column(name = "accounting_module_enabled", nullable = false)
 private boolean accountingModuleEnabled;

 @Column(name = "analytics_module_enabled", nullable = false)
 private boolean analyticsModuleEnabled;

 @Column(name = "automation_module_enabled", nullable = false)
 private boolean automationModuleEnabled;

 private TenantSettings(UUID tenantId) {

  validateTenantId(tenantId);

  this.tenantId = tenantId;

  // ------------------------------------------------
  // DEFAULT LOCALIZATION
  // ------------------------------------------------

  this.timezone = "Africa/Nairobi";
  this.currency = "KES";
  this.locale = "en_KE";

  // ------------------------------------------------
  // DEFAULT NOTIFICATIONS
  // ------------------------------------------------

  this.emailNotificationsEnabled = true;
  this.smsNotificationsEnabled = false;
  this.pushNotificationsEnabled = true;

  // ------------------------------------------------
  // DEFAULT MODULE ACCESS
  // ------------------------------------------------

  this.maintenanceModuleEnabled = true;
  this.accountingModuleEnabled = true;
  this.analyticsModuleEnabled = true;
  this.automationModuleEnabled = false;
 }

 public static TenantSettings defaultSettings(UUID tenantId) {

  return new TenantSettings(tenantId);
 }

 // ----------------------------------------------------------------
 // LOCALIZATION
 // ----------------------------------------------------------------

 public void updateLocalization(
         String timezone,
         String currency,
         String locale
 ) {

  validateTimezone(timezone);
  validateCurrency(currency);
  validateLocale(locale);

  this.timezone = timezone;
  this.currency = currency;
  this.locale = locale;
 }

 // ----------------------------------------------------------------
 // NOTIFICATION SETTINGS
 // ----------------------------------------------------------------

 public void enableEmailNotifications() {
  this.emailNotificationsEnabled = true;
 }

 public void disableEmailNotifications() {
  this.emailNotificationsEnabled = false;
 }

 public void enableSmsNotifications() {
  this.smsNotificationsEnabled = true;
 }

 public void disableSmsNotifications() {
  this.smsNotificationsEnabled = false;
 }

 public void enablePushNotifications() {
  this.pushNotificationsEnabled = true;
 }

 public void disablePushNotifications() {
  this.pushNotificationsEnabled = false;
 }

 // ----------------------------------------------------------------
 // MODULE MANAGEMENT
 // ----------------------------------------------------------------

 public void enableMaintenanceModule() {
  this.maintenanceModuleEnabled = true;
 }

 public void disableMaintenanceModule() {
  this.maintenanceModuleEnabled = false;
 }

 public void enableAccountingModule() {
  this.accountingModuleEnabled = true;
 }

 public void disableAccountingModule() {
  this.accountingModuleEnabled = false;
 }

 public void enableAnalyticsModule() {
  this.analyticsModuleEnabled = true;
 }

 public void disableAnalyticsModule() {
  this.analyticsModuleEnabled = false;
 }

 public void enableAutomationModule() {
  this.automationModuleEnabled = true;
 }

 public void disableAutomationModule() {
  this.automationModuleEnabled = false;
 }

 // ----------------------------------------------------------------
 // VALIDATION
 // ----------------------------------------------------------------

 private void validateTenantId(UUID tenantId) {

  if (tenantId == null) {
   throw new IllegalArgumentException(
           "Tenant ID cannot be null"
   );
  }
 }

 private void validateTimezone(String timezone) {

  if (timezone == null || timezone.isBlank()) {
   throw new IllegalArgumentException(
           "Timezone is required"
   );
  }
 }

 private void validateCurrency(String currency) {

  if (currency == null || currency.isBlank()) {
   throw new IllegalArgumentException(
           "Currency is required"
   );
  }
 }

 private void validateLocale(String locale) {

  if (locale == null || locale.isBlank()) {
   throw new IllegalArgumentException(
           "Locale is required"
   );
  }
 }
}