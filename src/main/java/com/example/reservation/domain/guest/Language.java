package com.example.reservation.domain.guest;

public enum Language {
    ENGLISH("English", "en"),
    KOREAN("한국어", "ko"),
    JAPANESE("日本語", "ja"),
    CHINESE_SIMPLIFIED("简体中文", "zh-CN"),
    CHINESE_TRADITIONAL("繁體中文", "zh-TW"),
    SPANISH("Español", "es"),
    FRENCH("Français", "fr"),
    GERMAN("Deutsch", "de"),
    ITALIAN("Italiano", "it"),
    PORTUGUESE("Português", "pt"),
    RUSSIAN("Русский", "ru"),
    ARABIC("العربية", "ar");
    
    private final String displayName;
    private final String code;
    
    Language(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public static Language fromCode(String code) {
        for (Language language : values()) {
            if (language.code.equals(code)) {
                return language;
            }
        }
        return ENGLISH; // 기본값
    }
}