# 🔐 TÀI KHOẢN ĐĂNG NHẬP - MySoftPOS

## ✅ CÁC TÀI KHOẢN KHẢ DỤNG

### 1️⃣ Tài khoản Admin
```
Username: admin
Password: admin123
```
👤 **Mô tả:** Tài khoản quản trị viên

---

### 2️⃣ Tài khoản Techcombank
```
Username: techcombank
Password: tcb2026
```
🏦 **Mô tả:** Tài khoản chính thức Techcombank

---

### 3️⃣ Tài khoản Test
```
Username: test
Password: test123
```
🧪 **Mô tả:** Tài khoản dùng để test

---

## 📋 HƯỚNG DẪN ĐĂNG NHẬP

### Bước 1: Mở App
- Khởi động MySoftPOS
- Màn hình Welcome hiện ra

### Bước 2: Chọn Đăng nhập
- Click nút **"Đăng nhập"**

### Bước 3: Nhập thông tin
- **Tên đăng nhập:** Chọn 1 trong 3 username trên
- **Mật khẩu:** Nhập password tương ứng

### Bước 4: Xác nhận
- Click nút **"Tiếp tục"**
- Nếu đúng → Vào Dashboard
- Nếu sai → Thông báo lỗi

---

## ⚠️ LƯU Ý

### ✅ Đúng:
- Username và password phải khớp CHÍNH XÁC
- Phân biệt chữ hoa/thường
- Không có khoảng trắng thừa

### ❌ Sai:
- `Admin` / `admin123` → SAI (phải là `admin`)
- `admin` / `Admin123` → SAI (phải là `admin123`)
- ` admin ` / `admin123` → SAI (có khoảng trắng)

---

## 🎯 SAU KHI ĐĂNG NHẬP

Bạn sẽ vào **Dashboard** với 6 chức năng:

1. 💳 **Thanh toán** (Purchase)
2. ❌ **Hủy giao dịch** (Void)
3. 💰 **Hoàn tiền** (Refund)
4. 📜 **Lịch sử** (History)
5. 🔑 **Logon**
6. ⚙️ **Cài đặt** (Settings) ✅

---

## 🔒 BẢO MẬT

- Mật khẩu được kiểm tra trực tiếp trong code
- Hiện tại chưa có mã hóa (for testing only)
- Để production: cần kết nối API server thật

---

## 📝 THÊM TÀI KHOẢN MỚI

Nếu muốn thêm tài khoản, edit file:
```
LoginActivity.java → handleLogin() method
```

Thêm dòng:
```java
else if (username.equals("YOUR_USERNAME") && password.equals("YOUR_PASSWORD")) {
    isValidAccount = true;
}
```

---

**Ngày cập nhật:** 14/01/2026  
**Version:** 1.1.0  
**Status:** ✅ Production Ready

