package ir.co.sadad.noticeapiconsumer.enums;

import lombok.Getter;

@Getter
public enum NoticeType {
    TRANSACTION("transaction"),
    CAMPAIGN("campaign");

    private final String value;

    NoticeType(String value) {
        this.value = value;
    }
}