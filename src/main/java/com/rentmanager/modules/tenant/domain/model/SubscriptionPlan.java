package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "subscription_plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan extends BaseEntity {

 @Column(name = "code", nullable = false, unique = true, length = 50)
 private String code;

 @Column(name = "name", nullable = false, length = 100)
 private String name;

 @Column(name = "description", length = 500)
 private String description;

 @Enumerated(EnumType.STRING)
 @Column(name = "billing_cycle", nullable = false, length = 50)
 private BillingCycle billingCycle;

 @Column(name = "max_properties")
 private Integer maxProperties;

 @Column(name = "max_units")
 private Integer maxUnits;

 @Column(name = "max_users")
 private Integer maxUsers;

 @Column(name = "max_storage_gb")
 private Integer maxStorageGb;

 @Column(name = "monthly_price", precision = 19, scale = 2)
 private BigDecimal monthlyPrice;

 @Column(name = "yearly_price", precision = 19, scale = 2)
 private BigDecimal yearlyPrice;

 @Column(name = "active", nullable = false)
 private boolean active;

 /**
  * Self-serve eligibility: FALSE plans (e.g. ENTERPRISE, custom pricing)
  * cannot be subscribed to through the tenant-facing switch API - they are
  * assigned manually after sales contact.
  */
 @Column(name = "self_service", nullable = false)
 private boolean selfService;

 /**
  * REQUIRED for JPA
  */


 /**
  * SINGLE RECONSTRUCTION CONSTRUCTOR (USED BY MAPSTRUCT)
  */
 protected SubscriptionPlan(
         String code,
         String name,
         String description,
         BillingCycle billingCycle,
         Integer maxProperties,
         Integer maxUnits,
         Integer maxUsers,
         Integer maxStorageGb,
         BigDecimal monthlyPrice,
         BigDecimal yearlyPrice,
         boolean active,
         boolean selfService
 ) {
  this.code = code;
  this.name = name;
  this.description = description;
  this.billingCycle = billingCycle;
  this.maxProperties = maxProperties;
  this.maxUnits = maxUnits;
  this.maxUsers = maxUsers;
  this.maxStorageGb = maxStorageGb;
  this.monthlyPrice = monthlyPrice;
  this.yearlyPrice = yearlyPrice;
  this.active = active;
  this.selfService = selfService;
 }

 /**
  * DOMAIN FACTORY (SAFE CREATION)
  */
 public static SubscriptionPlan create(
         String code,
         String name,
         BillingCycle billingCycle
 ) {
  if (code == null || code.isBlank()) {
   throw new IllegalArgumentException("Subscription plan code is required");
  }

  if (name == null || name.isBlank()) {
   throw new IllegalArgumentException("Subscription plan name is required");
  }

  return new SubscriptionPlan(
          code,
          name,
          null,
          billingCycle,
          null,
          null,
          null,
          null,
          null,
          null,
          true,
          true
  );
 }

 /**
  * SAFE RECONSTRUCTION (RECOMMENDED FOR MAPPERS)
  */
 public static SubscriptionPlan reconstruct(
         String code,
         String name,
         String description,
         BillingCycle billingCycle,
         Integer maxProperties,
         Integer maxUnits,
         Integer maxUsers,
         Integer maxStorageGb,
         BigDecimal monthlyPrice,
         BigDecimal yearlyPrice,
         boolean active,
         boolean selfService
 ) {
  return new SubscriptionPlan(
          code,
          name,
          description,
          billingCycle,
          maxProperties,
          maxUnits,
          maxUsers,
          maxStorageGb,
          monthlyPrice,
          yearlyPrice,
          active,
          selfService
  );
 }

 /**
  * REHYDRATION (RECOMMENDED FOR PERSISTENCE MAPPERS - RESTORES IDENTITY).
  * Same convention as Tenant / SubscriptionStandingOrder / 
  * SubscriptionPaymentRequest rehydrate(...) factories.
  */
 public static SubscriptionPlan rehydrate(
         UUID id,
         Long version,
         String code,
         String name,
         String description,
         BillingCycle billingCycle,
         Integer maxProperties,
         Integer maxUnits,
         Integer maxUsers,
         Integer maxStorageGb,
         BigDecimal monthlyPrice,
         BigDecimal yearlyPrice,
         boolean active,
         boolean selfService
 ) {
  SubscriptionPlan plan = new SubscriptionPlan(
          code,
          name,
          description,
          billingCycle,
          maxProperties,
          maxUnits,
          maxUsers,
          maxStorageGb,
          monthlyPrice,
          yearlyPrice,
          active,
          selfService
  );
  plan.setId(id);
  plan.setVersion(version);
  return plan;
 }

 public void deactivate() {
  this.active = false;
 }

 public boolean isSelfService() {
  return selfService;
 }

 public boolean supportsAdditionalProperties(int currentProperties) {
  return maxProperties == null || currentProperties < maxProperties;
 }

 public boolean supportsAdditionalUnits(int currentUnits) {
  return maxUnits == null || currentUnits < maxUnits;
 }
}