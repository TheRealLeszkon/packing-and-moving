# CLAUDE.md

# SurveyAgent Android Client

## Overview

This repository contains the native Android application for **SurveyAgent**, an AI-assisted packing and moving survey platform.

The Android application is responsible for:

- User authentication
- Creating and managing surveys
- Capturing photos and videos
- Uploading media to the backend
- Displaying AI-generated inventory
- Allowing manual edits
- Completing surveys

The backend already exists.

Treat the backend API and OpenAPI specification as the single source of truth.

Do **not** redesign backend endpoints or invent unsupported functionality.

---

# Project Goals

The application should be:

- Native
- Fast
- Modern
- Readable
- Production-ready
- Easy to extend

Prioritize maintainability over clever implementations.

Avoid unnecessary abstractions.

---

# Tech Stack

Use the following technologies unless explicitly instructed otherwise.

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

Do not introduce additional libraries without a strong justification.

---

# Reference Documents

Always treat these files as the project specification.

- openapi.json
- frontend-integration.md
- DESIGN.md

If there is a conflict:

OpenAPI > frontend-integration.md > DESIGN.md

---

# Development Philosophy

Keep the code simple.

Prefer explicit code over generic abstractions.

Avoid writing code that is "future-proof" at the expense of readability.

Do not overengineer.

Write code that a junior Android developer could comfortably understand.

---

# Architecture

Follow MVVM.

```
UI
│
▼
ViewModel
│
▼
Repository
│
▼
Retrofit API
│
▼
FastAPI Backend
```

Responsibilities:

UI

- Rendering
- User interaction
- Navigation

ViewModel

- UI state
- Business logic
- Calls Repository

Repository

- Network communication
- Error handling
- DTO mapping

Retrofit

- API communication only

Never place business logic inside Composables.

---

# Project Structure

```
app/

api/
    ApiService
    DTOs
    RetrofitInstance

repository/

model/

viewmodel/

ui/
    navigation/
    screens/
    components/
    theme/

camera/

utils/
```

Keep files focused.

Avoid files larger than roughly 400 lines whenever practical.

---

# UI Guidelines

Use Material 3 throughout.

Follow the provided design specification.

Support:

- Dark Mode
- Landscape
- Different screen sizes

Use consistent spacing.

Prefer cards over dense layouts.

Minimum touch target:

48dp

---

# Navigation

Use Navigation Compose.

Every screen should have a dedicated route.

Avoid passing large objects between screens.

Pass IDs.

Reload data when needed.

---

# State Management

Expose immutable StateFlow objects.

Example:

```
private val _uiState = MutableStateFlow(...)

val uiState: StateFlow<UiState> = _uiState
```

Compose should observe StateFlow.

Do not expose mutable state.

---

# Networking

Use Retrofit.

Use suspend functions.

Use Kotlin Serialization.

Never hardcode URLs.

The API base URL should come from BuildConfig or local.properties.

All API communication should pass through the Repository layer.

---

# Error Handling

Handle:

- No Internet
- Timeouts
- Authentication failures
- Backend errors
- Invalid responses

Display friendly error messages.

Never silently ignore exceptions.

---

# Images

Use Coil.

Do not manually decode Bitmaps unless necessary.

Images returned by the backend may use short-lived signed URLs.

Assume URLs can expire.

---

# Camera

Use CameraX.

The camera implementation will be built incrementally.

Do not add advanced camera features unless requested.

Focus on clean architecture first.

---

# Authentication

Use the backend authentication flow.

Do not implement authentication logic that differs from the backend contract.

Securely store:

- Access Token
- Refresh Token

Handle automatic token refresh.

---

# Code Style

Prefer descriptive names.

Avoid abbreviations.

Keep functions short.

Keep composables focused.

Extract reusable UI components.

Avoid duplicate logic.

Comment only when it improves understanding.

Good code should mostly explain itself.

---

# Backend Contract

The backend is authoritative.

Never invent endpoints.

Never change request or response models.

If backend functionality is missing:

Leave a TODO.

Do not implement fake functionality.

---

# Development Process

Build the project incrementally.

After completing a major feature:

- Ensure it compiles.
- Resolve warnings where reasonable.
- Keep the project in a runnable state.

Prefer many small commits over one massive change.

---

# Performance

Avoid unnecessary recompositions.

Use remember where appropriate.

Use LazyColumn instead of Column for long lists.

Avoid blocking the UI thread.

Network operations should always use Coroutines.

---

# Testing During Development

Before considering a feature complete:

- Project builds successfully
- Navigation works
- Screen renders correctly
- API integration functions
- Loading states exist
- Error states exist
- Empty states exist

---

# Communication

When implementing a feature:

Briefly explain:

- What is being built
- Which files are added or modified
- Any assumptions made

Do not produce long explanations.

Focus on implementation.

If requirements are ambiguous, choose the simplest reasonable solution and document the assumption.

---

# Goal

Build a clean, modern Android application that faithfully implements the provided design while integrating seamlessly with the existing FastAPI backend.

The application should be easy to understand, easy to maintain, and straightforward to extend as the backend evolves.
