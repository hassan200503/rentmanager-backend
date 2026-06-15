package com.rentmanager.modules.tenant.domain.valueobject;

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
public class BrandingSettings {

 private String logoUrl;
 private String faviconUrl;
 private String primaryColor;
 private String secondaryColor;

 // --------------------------------------------------
 // SAFE FACTORY
 // --------------------------------------------------

 public static BrandingSettings create(
         String logoUrl,
         String faviconUrl,
         String primaryColor,
         String secondaryColor
 ) {

  validateColor(primaryColor, "Primary color");
  validateColor(secondaryColor, "Secondary color");

  return BrandingSettings.builder()
          .logoUrl(normalizeUrl(logoUrl))
          .faviconUrl(normalizeUrl(faviconUrl))
          .primaryColor(primaryColor != null ? primaryColor.trim() : null)
          .secondaryColor(secondaryColor != null ? secondaryColor.trim() : null)
          .build();
 }

 // --------------------------------------------------
 // VALIDATION
 // --------------------------------------------------

 private static void validateColor(String color, String fieldName) {

  if (color == null || color.isBlank()) {
   return; // allow optional branding colors
  }

  if (!color.startsWith("#")) {
   throw new IllegalArgumentException(
           fieldName + " must be a valid hex color"
   );
  }

  if (!(color.length() == 4 || color.length() == 7)) {
   throw new IllegalArgumentException(
           fieldName + " must be valid hex format (#RGB or #RRGGBB)"
   );
  }
 }

 // --------------------------------------------------
 // NORMALIZATION
 // --------------------------------------------------

 private static String normalizeUrl(String url) {

  if (url == null || url.isBlank()) {
   return null;
  }

  return url.trim();
 }

 public static BrandingSettings of(
         String logoUrl,
         String faviconUrl,
         String primaryColor,
         String secondaryColor
 ) {
  return new BrandingSettings(
          logoUrl,
          faviconUrl,
          primaryColor,
          secondaryColor
  );
 }


 public static BrandingSettings defaultSettings() {
  return BrandingSettings.builder()
          .logoUrl(null)
          .faviconUrl(null)
          .primaryColor("#000000")
          .secondaryColor("#FFFFFF")
          .build();
 }
}