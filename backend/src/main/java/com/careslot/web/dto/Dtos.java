package com.careslot.web.dto;

import jakarta.validation.constraints.*;

public class Dtos {

    public record TenantSignupRequest(
            @NotBlank String tenantName,
            @NotBlank String adminName,
            @NotBlank @Email String adminEmail,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String fullName) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record UserDto(Long id, String email, String fullName, String role, Long tenantId) {}

    public record AuthResponse(UserDto user, Long tenantId, String slug,
                               String accessToken, String refreshToken) {}

    public record TenantDto(Long id, String name, String slug) {}

    public record ServiceDto(Long id, String name, String description,
                             int durationMinutes, int priceCents, boolean active) {}

    public record UpsertServiceRequest(
            @NotBlank String name, String description,
            @Min(5) @Max(480) int durationMinutes,
            @Min(0) int priceCents,
            Boolean active) {}

    public record ProviderDto(Long id, String name, String specialty, boolean active) {}

    public record CreateProviderRequest(
            @NotBlank String name, String specialty,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record WorkingHoursDto(int dayOfWeek, String startTime, String endTime) {}

    public record UpsertHoursRequest(
            @Min(1) @Max(7) int dayOfWeek,
            @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}") String startTime,
            @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}") String endTime) {}

    public record GenerateSlotsRequest(
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String fromDate,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String toDate,
            @Min(5) @Max(240) Integer slotMinutes) {}

    public record SlotDto(Long slotId, String startAt, String endAt, String status, Long serviceId) {}

    public record BookRequest(
            @NotNull Long slotId,
            @NotNull Long serviceId,
            @Size(max = 500) String notes) {}

    public record PaymentIntentDto(String providerRef, String clientSecret, String status,
                                   int amountCents, String currency, String provider) {}

    public record ConfirmPaymentRequest(boolean succeed) {}
}
