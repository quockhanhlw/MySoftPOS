package com.example.mysoftpos.utils.mcc;

import android.content.Context;

import com.example.mysoftpos.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps user-facing business type labels to the internal DE18/MCC code.
 * UI should only show the descriptive label, while transaction/runtime layers
 * consume the 4-digit MCC.
 */
public final class BusinessTypeMccMapper {

    private static final Pattern LEGACY_CODE_PREFIX = Pattern.compile("^(\\d{4})\\s*[-:\\u2013]\\s*(.+)$");
    private static final LinkedHashMap<String, String> CODE_TO_LABEL_EN = new LinkedHashMap<>();
    private static final Map<String, String> NORMALIZED_LABEL_TO_CODE = new LinkedHashMap<>();
    private static final List<String> CODE_ORDER;
    private static final List<String> DISPLAY_OPTIONS_EN;

    static {
        register("0700",
                "Agriculture / Veterinary medicine / Veterinary services / Distilled spirits / Wine production",
                "Nong nghiep / Thuoc thu y / Dich vu thu y / San xuat ruou manh / San xuat ruou vang");
        register("4000",
                "Transportation / Rail / Taxi-limousine / Boat charter-rental / Airports-air terminals",
                "Van tai / Duong sat / Taxi / limousine / Thue tau / cho thue thuyen / San bay / bai dap / nha ga hang khong");
        register("4111", "Commuter passenger transport", "Van tai hanh khach chung");
        register("4112", "Passenger railway", "Duong sat cho khach");
        register("4131", "Bus lines", "Tuyen xe buyt");
        register("4789", "Other transportation services", "Dich vu van tai khac");
        register("4784", "Bridge and road tolls", "Thu phi cau va duong");
        register("4800", "Utility services", "Dich vu tien ich");
        register("4814", "Telecommunication services", "Vien thong");
        register("4816", "Computer network and information services", "Dich vu mang may tinh / thong tin");
        register("4899", "Cable and paid television", "Truyen hinh cap / truyen hinh tra tien");
        register("4900", "Electricity / Gas / Water / Sanitation", "Dien / Gas / Nuoc / Ve sinh moi truong");
        register("5000",
                "Retail services (bookstore, tobacco, stationery, computer equipment, building materials, office furniture, home appliances, food-beverage, florist-gardening, mobile phones, duty free, bakery)",
                "Ban le / Nha sach / bao / tap chi / Cua hang xi ga / thuoc la xi ga / Van phong pham / Thiet bi may tinh / Cua hang vat lieu xay dung / Noi that van phong / thuong mai / Thiet bi gia dung / Do an va do uong / Hoa / vat tu lam vuon / Cua hang dien thoai di dong / Cua hang mien thue / Cua hang banh");
        register("5411", "Grocery and supermarket", "Tap hoa / sieu thi / O to va phuong tien");
        register("5500",
                "Auto and truck dealers-services-parts-leasing-gas stations and related",
                "Dai ly o to / xe tai moi va cu / Dich vu sua chua o to / Phu tung o to / Cho thue / leasing o to xe tai / Cua hang lop xe / Cua hang phu kien o to / Tram dich vu / Tram xang / Dai ly thuyen / Dai ly motor home");

        CODE_ORDER = Collections.unmodifiableList(new ArrayList<>(CODE_TO_LABEL_EN.keySet()));
        DISPLAY_OPTIONS_EN = Collections.unmodifiableList(new ArrayList<>(CODE_TO_LABEL_EN.values()));
    }

    private BusinessTypeMccMapper() {
    }

    public static List<String> getDisplayOptions() {
        return DISPLAY_OPTIONS_EN;
    }

    public static List<String> getDisplayOptions(Context context) {
        if (context == null) {
            return DISPLAY_OPTIONS_EN;
        }
        String[] localized = context.getResources().getStringArray(R.array.register_business_type_options);
        if (localized.length == CODE_ORDER.size()) {
            return Arrays.asList(localized);
        }
        return DISPLAY_OPTIONS_EN;
    }

    public static String toMcc(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (CODE_TO_LABEL_EN.containsKey(value)) {
            return value;
        }
        Matcher matcher = LEGACY_CODE_PREFIX.matcher(value);
        if (matcher.matches()) {
            String code = matcher.group(1);
            if (CODE_TO_LABEL_EN.containsKey(code)) {
                return code;
            }
        }
        String mapped = NORMALIZED_LABEL_TO_CODE.get(normalize(value));
        return mapped != null ? mapped : "";
    }

    public static String toDisplay(String rawValue) {
        return toDisplay(null, rawValue);
    }

    public static String toDisplay(Context context, String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }

        // Preserve the original descriptive label for legacy persisted values
        // formatted as "MCC - Label" regardless of current locale.
        Matcher legacyMatcher = LEGACY_CODE_PREFIX.matcher(value);
        if (legacyMatcher.matches()) {
            String legacyLabel = legacyMatcher.group(2);
            return legacyLabel != null ? legacyLabel.trim() : value;
        }

        String mcc = toMcc(value);
        if (mcc.isEmpty()) {
            return value;
        }

        if (context != null) {
            String[] localized = context.getResources().getStringArray(R.array.register_business_type_options);
            int index = CODE_ORDER.indexOf(mcc);
            if (index >= 0 && index < localized.length) {
                return localized[index];
            }
        }
        return CODE_TO_LABEL_EN.getOrDefault(mcc, value);
    }

    public static boolean isSupportedSelection(String rawValue) {
        return !toMcc(rawValue).isEmpty();
    }

    private static void register(String code, String labelEn, String labelViAscii) {
        CODE_TO_LABEL_EN.put(code, labelEn);
        NORMALIZED_LABEL_TO_CODE.put(normalize(labelEn), code);
        NORMALIZED_LABEL_TO_CODE.put(normalize(labelViAscii), code);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String noDiacritics = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noDiacritics.replace('\u0111', 'd').replace('\u0110', 'd');
    }
}
