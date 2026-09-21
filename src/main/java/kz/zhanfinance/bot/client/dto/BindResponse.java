package kz.zhanfinance.bot.client.dto;

public record BindResponse(
        boolean success,
        Long userId,
        String fullName,
        String email,
        String role
) {}
