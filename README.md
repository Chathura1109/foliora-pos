# Foliora POS

Foliora is an Android point-of-sale and inventory application for a small plant shop. It provides separate owner and cashier experiences while keeping local Room data synchronised with Firebase.

## Main features

- Owner and cashier authentication with role-based access
- Product, category, supplier and customer management
- Batch-based inventory with separate cost and selling prices
- Purchases, stock adjustments and strict online checkout
- Sales history, receipts and PDF printing
- Offline Room storage with Firestore and WorkManager synchronisation
- Product image upload through Cloudinary

## Architecture

The app follows a layered MVVM structure:

`Jetpack Compose UI → ViewModels → Repositories → Room / Firebase`

Hilt provides dependencies, Navigation Compose controls screen routing, and Kotlin coroutines handle asynchronous work.

## Technology

- Kotlin and Jetpack Compose
- Room Database
- Firebase Authentication and Cloud Firestore
- WorkManager
- Hilt
- CameraX and Cloudinary

## Run locally

1. Open the project in Android Studio.
2. Add your Firebase `google-services.json` file inside the `app` directory.
3. Configure the required Firebase Authentication users and Firestore data.
4. Build and run on an Android device or emulator running Android 8.0 or newer.

```bash
./gradlew assembleDebug
```
