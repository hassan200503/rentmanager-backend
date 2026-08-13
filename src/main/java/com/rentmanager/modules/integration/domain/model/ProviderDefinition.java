package com.rentmanager.modules.integration.domain.model;

import java.util.List;

/**
 * Static, code-level catalog entry describing one external provider the
 * platform depends on. The control plane UI renders from these definitions;
 * the credential values themselves live in {@link IntegrationConfig}.
 */
public record ProviderDefinition(
        String key,
        String displayName,
        String category,
        String docsUrl,
        boolean supportsTestConnection,
        List<ProviderField> fields
) {
    public ProviderField field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }

    public boolean isSecret(String key) {
        ProviderField field = field(key);
        return field != null && field.secret();
    }
}