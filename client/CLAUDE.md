# CLAUDE.md

## Project

This project is a native Android client for an AI-assisted packing and moving survey application.

The backend already exists.

Do not redesign backend functionality.

Treat the backend API as the source of truth.

---

# Development Philosophy

Keep the code:

- Simple
- Readable
- Explicit
- Modular

Avoid unnecessary abstraction.

Avoid "enterprise" architecture.

Avoid premature optimization.

Prefer maintainability over cleverness.

---

# Tech Stack

Use:

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Navigation Compose
- Retrofit
- Kotlin Serialization
- Coroutines
- StateFlow
- Coil
- CameraX

Do not introduce additional libraries unless there is a clear benefit.

---

# Architecture

Organize the project as:

```
app/

api/
repository/
model/
viewmodel/

ui/
    screens/
    components/
    navigation/
    theme/

camera/

utils/
```

One screen per file whenever practical.

Business logic belongs inside repositories and ViewModels.

Composable functions should remain focused on UI.

---

# Networking

Use Retrofit.

Use suspend functions.

Use Kotlin Serialization.

The API base URL should come from BuildConfig or local.properties.

Never hardcode URLs.

---

# State Management

UI should observe StateFlow exposed by ViewModels.

Avoid passing mutable state through multiple composables.

Prefer immutable UI state models.

---

# Camera

Use CameraX.

Support:

- capture image
- preview
- gallery picker
- multiple images

Store captured images temporarily until uploaded.

---

# Image Loading

Use Coil.

Do not manually decode Bitmaps unless required.

---

# Error Handling

Handle:

- No internet
- Upload failures
- HTTP errors
- Backend unavailable
- Timeout

Display user-friendly messages.

Never silently ignore exceptions.

---

# UI

Use Material 3 components.

Support dark mode.

Use:

- Cards
- TopAppBar
- NavigationBar
- Snackbar
- Dialogs

Follow Material spacing (8dp grid).

Large touch targets.

Avoid crowded layouts.

---

# Code Style

Prefer descriptive names.

Keep functions small.

Keep classes focused.

Avoid files exceeding ~400 lines where practical.

Avoid duplicate logic.

Comment only when it improves understanding.

---

# Backend Contract

Available endpoints:

POST /upload

GET /processing

GET /processing/{id}

Do not invent endpoints.

If functionality requires a missing endpoint, leave a clear TODO instead of implementing unsupported behavior.

---

# Expected Behaviour

The application should:

- capture images
- upload them to the backend
- poll processing status
- display AI-generated inventory
- browse previous surveys

The application should be production-ready, easy to understand, and easy to extend as additional backend endpoints become available.
