package kz.zhanfinance.bot.client.dto;

public record ClientProfileDto(
        boolean linked,
        Long userId,
        String fullName,
        String email,
        String phone,
        String companyName,
        String role
) {}
