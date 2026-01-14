package com.example.mysoftpos;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class RegisterActivity extends AppCompatActivity {

    private ImageView btnBack;
    private RadioGroup rgAccountType;
    private RadioButton rbPersonal, rbBusiness;
    private EditText etAccountNumber;
    private EditText etBankName;
    private EditText etAccountName;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private ImageView btnShowPassword;
    private ImageView btnShowConfirmPassword;
    private CheckBox cbTerms;
    private TextView tvTermsText;
    private CardView btnRegister;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize views
        btnBack = findViewById(R.id.btnBack);
        rgAccountType = findViewById(R.id.rgAccountType);
        rbPersonal = findViewById(R.id.rbPersonal);
        rbBusiness = findViewById(R.id.rbBusiness);
        etAccountNumber = findViewById(R.id.etAccountNumber);
        etBankName = findViewById(R.id.etBankName);
        etAccountName = findViewById(R.id.etAccountName);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnShowPassword = findViewById(R.id.btnShowPassword);
        btnShowConfirmPassword = findViewById(R.id.btnShowConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTermsText = findViewById(R.id.tvTermsText);
        btnRegister = findViewById(R.id.btnRegister);

        // Set default selection
        rbPersonal.setChecked(true);

        // Setup clickable terms text
        setupTermsText();

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Show/Hide password
        btnShowPassword.setOnClickListener(v -> togglePasswordVisibility());

        // Show/Hide confirm password
        btnShowConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());

        // Register button
        btnRegister.setOnClickListener(v -> handleRegister());
    }

    private void setupTermsText() {
        String fullText = "Bằng việc Đăng ký, bạn đã đồng ý với Điều khoản & Điều kiện cũng Chính sách bảo mật của Techcombank ePOS";
        SpannableString spannableString = new SpannableString(fullText);

        // Clickable span for "Điều khoản & Điều kiện"
        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                showTermsDialog();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4A9EFF"));
                ds.setUnderlineText(false);
            }
        };

        // Clickable span for "Chính sách bảo mật"
        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                showPrivacyDialog();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4A9EFF"));
                ds.setUnderlineText(false);
            }
        };

        int termsStart = fullText.indexOf("Điều khoản & Điều kiện");
        int termsEnd = termsStart + "Điều khoản & Điều kiện".length();
        spannableString.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int privacyStart = fullText.indexOf("Chính sách bảo mật");
        int privacyEnd = privacyStart + "Chính sách bảo mật".length();
        spannableString.setSpan(privacySpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvTermsText.setText(spannableString);
        tvTermsText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCanceledOnTouchOutside(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        TextView tvDialogContent = dialog.findViewById(R.id.tvDialogContent);

        tvDialogTitle.setText("Điều khoản & Điều kiện");
        tvDialogContent.setText(getTermsContent());

        dialog.show();
    }

    private void showPrivacyDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCanceledOnTouchOutside(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        TextView tvDialogContent = dialog.findViewById(R.id.tvDialogContent);

        tvDialogTitle.setText("Chính sách bảo mật");
        tvDialogContent.setText(getPrivacyContent());

        dialog.show();
    }

    private String getTermsContent() {
        return "ĐIỀU KHOẢN & ĐIỀU KIỆN SỬ DỤNG DỊCH VỤ\n\n" +
                "1. GIỚI THIỆU\n" +
                "Điều khoản và Điều kiện này quy định việc sử dụng dịch vụ MySoftPOS do Techcombank cung cấp.\n\n" +
                "2. CHẤP NHẬN ĐIỀU KHOẢN\n" +
                "Bằng việc đăng ký và sử dụng dịch vụ, bạn đồng ý tuân thủ các điều khoản và điều kiện này.\n\n" +
                "3. QUYỀN VÀ TRÁCH NHIỆM\n" +
                "- Người dùng có trách nhiệm bảo mật thông tin tài khoản\n" +
                "- Người dùng phải cung cấp thông tin chính xác khi đăng ký\n" +
                "- Không được sử dụng dịch vụ cho mục đích bất hợp pháp\n\n" +
                "4. THANH TOÁN VÀ PHÍ DỊCH VỤ\n" +
                "- Phí dịch vụ sẽ được công bố rõ ràng trước khi giao dịch\n" +
                "- Mọi giao dịch đều tuân thủ quy định của Ngân hàng Nhà nước\n\n" +
                "5. BẢO MẬT THÔNG TIN\n" +
                "- Techcombank cam kết bảo mật thông tin khách hàng\n" +
                "- Thông tin chỉ được sử dụng cho mục đích cung cấp dịch vụ\n\n" +
                "6. GIỚI HẠN TRÁCH NHIỆM\n" +
                "- Techcombank không chịu trách nhiệm cho thiệt hại gián tiếp\n" +
                "- Người dùng tự chịu trách nhiệm về việc sử dụng dịch vụ\n\n" +
                "7. THAY ĐỔI ĐIỀU KHOẢN\n" +
                "Techcombank có quyền thay đổi điều khoản và sẽ thông báo trước cho người dùng.\n\n" +
                "8. LIÊN HỆ\n" +
                "Mọi thắc mắc vui lòng liên hệ: support@techcombank.com.vn hoặc hotline: 1800-588-822";
    }

    private String getPrivacyContent() {
        return "CHÍNH SÁCH BẢO MẬT\n\n" +
                "1. THU THẬP THÔNG TIN\n" +
                "Chúng tôi thu thập các thông tin sau:\n" +
                "- Thông tin cá nhân: Họ tên, số điện thoại, email\n" +
                "- Thông tin tài khoản ngân hàng\n" +
                "- Thông tin giao dịch\n" +
                "- Dữ liệu sử dụng ứng dụng\n\n" +
                "2. MỤC ĐÍCH SỬ DỤNG\n" +
                "Thông tin được sử dụng để:\n" +
                "- Cung cấp và cải thiện dịch vụ\n" +
                "- Xác thực danh tính người dùng\n" +
                "- Xử lý giao dịch thanh toán\n" +
                "- Gửi thông báo quan trọng\n" +
                "- Phân tích và nghiên cứu thị trường\n\n" +
                "3. CHIA SẺ THÔNG TIN\n" +
                "Chúng tôi cam kết:\n" +
                "- Không bán thông tin cá nhân cho bên thứ ba\n" +
                "- Chỉ chia sẻ khi có yêu cầu pháp lý\n" +
                "- Bảo vệ thông tin bằng các biện pháp kỹ thuật cao\n\n" +
                "4. BẢO MẬT DỮ LIỆU\n" +
                "- Mã hóa dữ liệu truyền tải (SSL/TLS)\n" +
                "- Lưu trữ an toàn trên hệ thống bảo mật\n" +
                "- Kiểm soát truy cập nghiêm ngặt\n" +
                "- Sao lưu định kỳ\n\n" +
                "5. QUYỀN CỦA NGƯỜI DÙNG\n" +
                "Bạn có quyền:\n" +
                "- Truy cập và xem thông tin cá nhân\n" +
                "- Yêu cầu chỉnh sửa thông tin không chính xác\n" +
                "- Xóa tài khoản và dữ liệu\n" +
                "- Từ chối nhận thông tin marketing\n\n" +
                "6. COOKIES VÀ THEO DÕI\n" +
                "- Sử dụng cookies để cải thiện trải nghiệm\n" +
                "- Có thể tắt cookies trong cài đặt trình duyệt\n\n" +
                "7. BẢO MẬT TRẺ EM\n" +
                "Dịch vụ dành cho người từ đủ 18 tuổi trở lên.\n\n" +
                "8. CẬP NHẬT CHÍNH SÁCH\n" +
                "Chính sách có thể được cập nhật. Chúng tôi sẽ thông báo về những thay đổi quan trọng.\n\n" +
                "9. LIÊN HỆ\n" +
                "Email: privacy@techcombank.com.vn\n" +
                "Hotline: 1800-588-822\n" +
                "Địa chỉ: Tòa nhà Techcombank, Hà Nội";
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnShowPassword.setImageResource(R.drawable.ic_eye_off);
            isPasswordVisible = false;
        } else {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnShowPassword.setImageResource(R.drawable.ic_eye);
            isPasswordVisible = true;
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            etConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnShowConfirmPassword.setImageResource(R.drawable.ic_eye_off);
            isConfirmPasswordVisible = false;
        } else {
            etConfirmPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnShowConfirmPassword.setImageResource(R.drawable.ic_eye);
            isConfirmPasswordVisible = true;
        }
        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
    }

    private void handleRegister() {
        // Get selected account type
        int selectedTypeId = rgAccountType.getCheckedRadioButtonId();
        String accountType = "";
        if (selectedTypeId == R.id.rbPersonal) {
            accountType = "Hộ kinh doanh";
        } else if (selectedTypeId == R.id.rbBusiness) {
            accountType = "Doanh nghiệp";
        }

        String accountNumber = etAccountNumber.getText().toString().trim();
        String bankName = etBankName.getText().toString().trim();
        String accountName = etAccountName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (accountNumber.isEmpty()) {
            etAccountNumber.setError("Vui lòng nhập số tài khoản");
            etAccountNumber.requestFocus();
            return;
        }

        if (accountNumber.length() < 9) {
            etAccountNumber.setError("Số tài khoản phải có ít nhất 9 chữ số");
            etAccountNumber.requestFocus();
            return;
        }

        if (bankName.isEmpty()) {
            etBankName.setError("Vui lòng nhập tên ngân hàng");
            etBankName.requestFocus();
            return;
        }

        if (accountName.isEmpty()) {
            etAccountName.setError("Vui lòng nhập tên tài khoản");
            etAccountName.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 8) {
            etPassword.setError("Mật khẩu phải có ít nhất 8 ký tự");
            etPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Vui lòng nhập lại mật khẩu");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Mật khẩu không khớp");
            etConfirmPassword.requestFocus();
            return;
        }

        // Check terms and conditions
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Vui lòng đồng ý với điều khoản và chính sách bảo mật", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Implement actual registration logic
        Toast.makeText(this, "Đăng ký thành công với loại: " + accountType, Toast.LENGTH_SHORT).show();
        finish();
    }
}
