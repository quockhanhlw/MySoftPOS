package com.example.mysoftpos.utils.logging;
import com.example.mysoftpos.utils.logging.ResponseCodeHelper;

import java.util.HashMap;
import java.util.Map;

public class ResponseCodeHelper {

    private static final Map<String, String> MESSAGES = new HashMap<>();

    static {
        MESSAGES.put("00", "Giao dịch thành công");
        MESSAGES.put("01", "Vui lòng liên hệ ngân hàng phát hành");
        MESSAGES.put("03", "Đơn vị chấp nhận thẻ không hợp lệ");
        MESSAGES.put("04", "Thẻ bị giữ lại");
        MESSAGES.put("05", "Không thể xử lý giao dịch / Lỗi không xác định");
        MESSAGES.put("12", "Giao dịch không hợp lệ");
        MESSAGES.put("13", "Số tiền không hợp lệ (hoặc sai đơn vị tiền tệ)");
        MESSAGES.put("14", "Số thẻ không hợp lệ (Sai định dạng/Check digit)");
        MESSAGES.put("15", "Không tìm thấy ngân hàng phát hành");
        MESSAGES.put("21", "Thẻ chưa được kích hoạt hoặc không hợp lệ");
        MESSAGES.put("25", "Không tìm thấy giao dịch gốc (khi Reversal/Void)");
        MESSAGES.put("30", "Lỗi định dạng tin nhắn (Format sai)");
        MESSAGES.put("34", "Nghi ngờ gian lận");
        MESSAGES.put("39", "Tài khoản tín dụng không hợp lệ");
        MESSAGES.put("41", "Thẻ đã báo mất");
        MESSAGES.put("43", "Thẻ đã báo bị đánh cắp");
        MESSAGES.put("51", "Số dư không đủ để thực hiện giao dịch");
        MESSAGES.put("53", "Tài khoản tiết kiệm không hợp lệ");
        MESSAGES.put("54", "Thẻ hết hạn sử dụng");
        MESSAGES.put("55", "Sai mã PIN");
        MESSAGES.put("57", "Giao dịch không được phép (với chủ thẻ này)");
        MESSAGES.put("58", "Giao dịch không được phép tại thiết bị này");
        MESSAGES.put("59", "Nghi ngờ gian lận");
        MESSAGES.put("61", "Vượt quá hạn mức số tiền giao dịch");
        MESSAGES.put("62", "Thẻ bị hạn chế (theo chính sách NH)");
        MESSAGES.put("63", "Vi phạm an toàn bảo mật (Lỗi MAC, Key...)");
        MESSAGES.put("64", "Số tiền giao dịch gốc không khớp");
        MESSAGES.put("65", "Vượt quá số lần giao dịch cho phép trong ngày");
        MESSAGES.put("68", "Giao dịch bị Time-out (Quá giờ)");
        MESSAGES.put("75", "Nhập sai PIN quá số lần cho phép");
        MESSAGES.put("76", "Tài khoản không hợp lệ (hoặc không tồn tại)");
        MESSAGES.put("90", "Hệ thống đang quyết toán (Cut-off)");
        MESSAGES.put("91", "Ngân hàng phát hành hoặc Switch không sẵn sàng");
        MESSAGES.put("92", "Không tìm được đường truyền tới NH phát hành");
        MESSAGES.put("94", "Giao dịch bị trùng lặp");
        MESSAGES.put("96", "Lỗi hệ thống");
    }

    public static String getMessage(String rc) {
        if (rc == null) return "Lỗi không xác định";
        String msg = MESSAGES.get(rc);
        if (msg == null) {
            return "Lỗi không xác định (RC: " + rc + ")";
        }
        return msg;
    }
}






