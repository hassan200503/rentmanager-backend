package com.rentmanager.modules.tenant.application.command.usecase;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
public interface UploadTenantLogoUseCase {
TenantResponse execute(UUID tenantId, MultipartFile file);
}