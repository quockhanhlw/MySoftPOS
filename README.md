# MySoftPOS

MySoftPOS is an Android-based Point of Sale (POS) and SoftPOS application designed to process payments, handle NFC interactions, and manage store transactions. It uses modern Android development practices and supports both offline and online operations.

## ✨ Features

- **Authentication & Security:** Secure Login, Registration, Forgot Password, and Session management. Includes role-based access control (Admin / User).
- **SoftPOS Capabilities:** NFC reading for contactless cards, EMV data parsing, and ISO8583 message formatting.
- **Transactions:** Process purchases, check balance inquiries, and view transaction history and details.
- **Admin Management:** Comprehensive User Management and Transaction Management portals.
- **Offline First & Sync:** Utilizes local database caching for offline mode and uses WorkManager for reliable background data synchronization with the backend server.
- **Multi-language Support:** Seamless switching between English (EN) and Vietnamese (VI) without restarting the app.
- **Test Suite:** Built-in extensive Test Suite for batch testing, performance benchmarking, and multi-threaded test running.

## 🛠 Tech Stack & Architecture

- **Language:** Java
- **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture (`data`, `domain`, `ui`, `di`)
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 36

### Key Libraries & Components
- **UI & Navigation:** AndroidX AppCompat, Material Design, ConstrainLayout, ViewBinding.
- **Networking:** Retrofit2 & OkHttp3 (with logging interceptor) for backend API communication.
- **Local Data:** AndroidX Room Database for local caching and offline capabilities.
- **Background Tasks:** AndroidX WorkManager for background synchronization.
- **Hardware:** Android NFC API (`android.hardware.nfc`).

## 📂 Project Structure

- `data/`: Contains Local (Room DB, Entities, DAOs) and Remote (Retrofit API definitions) data sources.
- `domain/`: Business logic, Repositories interfaces, and Use Cases.
- `ui/`: UI Layer containing Activities and Fragments, organized by feature (`auth`, `admin`, `dashboard`, `purchase`, `balance`, `settings`).
- `viewmodel/`: ViewModels connecting the UI layer to the Domain layer.
- `di/`: Dependency Injection modules.
- `iso8583/`: Handles parsing and packing of ISO8583 financial transaction messages.
- `nfc/`: Utilities and logic for reading NFC tags and EMV cards.
- `utils/`: Helper classes (Security, Locale Management, Formatting).
- `testsuite/`: Dedicated modules for in-app testing, batch running, and performance testing.

## ⚙️ Setup & Installation

1. Clone this repository.
2. Open the project in **Android Studio**.
3. Let Gradle sync and download dependencies.
4. Set up an Android device with NFC capabilities (or use an emulator for non-NFC testing).
5. Build and run the app.

## 🌐 Localization

The app fully supports English (default) and Vietnamese (`values-vi`). Language can be toggled via the settings menu or directly from the authentication screens (Welcome, Login, Register) for quick access.
