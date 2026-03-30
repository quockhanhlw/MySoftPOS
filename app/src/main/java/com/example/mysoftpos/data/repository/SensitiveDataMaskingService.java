package com.example.mysoftpos.data.repository;

import com.example.mysoftpos.utils.security.PanMasker;

/**
 * Masks sensitive card fragments before they are persisted or synced.
 */
public final class SensitiveDataMaskingService {

    public String maskIsoHex(String rawHex) {
        if (rawHex == null || rawHex.trim().isEmpty()) {
            return rawHex;
        }
        return PanMasker.maskHex(PanMasker.mask(rawHex));
    }
}

