package com.example.mysoftpos.utils.mcc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps user-facing business type labels to the internal DE18/MCC code.
 * UI should only show the descriptive label, while transaction/runtime layers
 * consume the 4-digit MCC.
 */
public final class BusinessTypeMccMapper {

    private static final Pattern LEGACY_CODE_PREFIX = Pattern.compile("^(\\d{4})\\s*[-:–]\\s*(.+)$");
    private static final LinkedHashMap<String, String> CODE_TO_LABEL = new LinkedHashMap<>();
    private static final Map<String, String> NORMALIZED_LABEL_TO_CODE = new LinkedHashMap<>();
    private static final List<String> DISPLAY_OPTIONS;

    static {
        register("0700", "Nông nghiệp / Thuốc thú y / Dịch vụ thú y / Sản xuất rượu mạnh / Sản xuất rượu vang");
        register("4000", "Vận tải / Đường sắt / Taxi / limousine / Thuê tàu / cho thuê thuyền / Sân bay / bãi đáp / nhà ga hàng không");
        register("4111", "Vận tải hành khách chung");
        register("4112", "Đường sắt chở khách");
        register("4131", "Tuyến xe buýt");
        register("4789", "Dịch vụ vận tải khác");
        register("4784", "Thu phí cầu và đường");
        register("4800", "Dịch vụ tiện ích");
        register("4814", "Viễn thông");
        register("4816", "Dịch vụ mạng máy tính / thông tin");
        register("4899", "Truyền hình cáp / truyền hình trả tiền");
        register("4900", "Điện / Gas / Nước / Vệ sinh môi trường");
        register("5000", "Bán lẻ / Nhà sách / báo / tạp chí / Cửa hàng xì gà / thuốc lá xì gà / Văn phòng phẩm / Thiết bị máy tính / Cửa hàng vật liệu xây dựng / Nội thất văn phòng / thương mại / Thiết bị gia dụng / Đồ ăn và đồ uống / Hoa / vật tư làm vườn / Cửa hàng điện thoại di động / Cửa hàng miễn thuế / Cửa hàng bánh");
        register("5411", "Tạp hóa / siêu thị / Ô tô và phương tiện");
        register("5500", "Đại lý ô tô / xe tải mới và cũ / Dịch vụ sửa chữa ô tô / Phụ tùng ô tô / Cho thuê / leasing ô tô xe tải / Cửa hàng lốp xe / Cửa hàng phụ kiện ô tô / Trạm dịch vụ / Trạm xăng / Đại lý thuyền / Đại lý motor home");
        DISPLAY_OPTIONS = Collections.unmodifiableList(new ArrayList<>(CODE_TO_LABEL.values()));
    }

    private BusinessTypeMccMapper() {
    }

    public static List<String> getDisplayOptions() {
        return DISPLAY_OPTIONS;
    }

    public static String toMcc(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (CODE_TO_LABEL.containsKey(value)) {
            return value;
        }
        Matcher matcher = LEGACY_CODE_PREFIX.matcher(value);
        if (matcher.matches()) {
            String code = matcher.group(1);
            if (CODE_TO_LABEL.containsKey(code)) {
                return code;
            }
        }
        String mapped = NORMALIZED_LABEL_TO_CODE.get(normalize(value));
        return mapped != null ? mapped : "";
    }

    public static String toDisplay(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (CODE_TO_LABEL.containsKey(value)) {
            return CODE_TO_LABEL.get(value);
        }
        Matcher matcher = LEGACY_CODE_PREFIX.matcher(value);
        if (matcher.matches()) {
            String code = matcher.group(1);
            String label = CODE_TO_LABEL.get(code);
            String legacyLabel = matcher.group(2);
            return label != null ? label : (legacyLabel != null ? legacyLabel.trim() : value);
        }
        String code = NORMALIZED_LABEL_TO_CODE.get(normalize(value));
        return code != null ? CODE_TO_LABEL.get(code) : value;
    }

    public static boolean isSupportedSelection(String rawValue) {
        return !toMcc(rawValue).isEmpty();
    }

    private static void register(String code, String label) {
        CODE_TO_LABEL.put(code, label);
        NORMALIZED_LABEL_TO_CODE.put(normalize(label), code);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
