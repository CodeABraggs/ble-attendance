# BLE Attendance

BLE Attendance is a mobile attendance system with an Android client and a Spring Boot/MySQL backend. Teachers can create classrooms and attendance sessions; students can join classrooms and verify attendance over Bluetooth Low Energy.

## Repository layout

- `App_Frontend/` - Android app built with Kotlin, Jetpack Compose, and Gradle.
- `Spring_Backend/ble-attendance-backend/` - Spring Boot REST API backed by MySQL.
- `Spring_Backend/DemoFIlter/` - separate Maven project kept in the repository.

## Requirements

- Android Studio with Android SDK 36 and a Java 11-compatible Android toolchain.
- Java 21 for the Spring backend.
- MySQL 8 or a compatible MySQL server.
- A physical Android device or emulator with Bluetooth support.

## Run the backend

1. Create a MySQL database named `ble_attendence`.
2. Set the database environment variables before starting the application:

   ```powershell
   $env:DB_URL = "jdbc:mysql://localhost:3306/ble_attendence"
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "your-password"
   ```

3. Start the API from `Spring_Backend/ble-attendance-backend`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

The API listens on port `8080` by default.

## Run the Android app

1. Open `App_Frontend/` in Android Studio.
2. Update `NetworkConfig.API_BASE_URL` in `App_Frontend/app/src/main/java/com/example/ble_app/data/Network.kt` to the reachable backend URL for your emulator or device. The URL must end with `/`.
3. Build and run the `app` configuration on an Android 8.0+ device or emulator.

For an Android emulator, a backend running on the host machine is usually reachable at `http://10.0.2.2:8080/`. A physical device needs the host computer's LAN address and network access to port `8080`.

## Tests

Run backend tests with:

```powershell
Set-Location Spring_Backend/ble-attendance-backend
.\mvnw.cmd test
```

Run Android unit tests from Android Studio or with:

```powershell
Set-Location App_Frontend
.\gradlew.bat test
```

## Configuration and security

Do not commit passwords, API keys, local database settings, generated build output, or machine-specific IDE files. Use environment variables for backend database credentials and keep device-specific Android endpoint changes local unless they are intentionally part of a release configuration.
