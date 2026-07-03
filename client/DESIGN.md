---
name: Kinetic Material
colors:
  surface: "#f9f9ff"
  surface-dim: "#cbdaff"
  surface-bright: "#f9f9ff"
  surface-container-lowest: "#ffffff"
  surface-container-low: "#f1f3ff"
  surface-container: "#eef1ff"
  surface-container-high: "#e8ecff"
  surface-container-highest: "#e2e7ff"
  on-surface: "#191b23"
  on-surface-variant: "#43474e"
  outline: "#73777f"
  outline-variant: "#c3c7cf"
  primary: "#ff4500"
  on-primary: "#ffffff"
  primary-container: "#ffdad0"
  on-primary-container: "#410000"
  secondary: "#ff8c00"
  on-secondary: "#ffffff"
  secondary-container: "#ffdf9d"
  on-secondary-container: "#251a00"
  tertiary: "#00bcd4"
  on-tertiary: "#ffffff"
  tertiary-container: "#c0efff"
  on-tertiary-container: "#001f24"
  error: "#ba1a1a"
  on-error: "#ffffff"
  error-container: "#ffdad6"
  on-error-container: "#410002"
typography:
  font-family: Work Sans, sans-serif
  headings:
    family: Work Sans
    weight: 700
  body:
    family: Work Sans
    weight: 400
  label:
    family: Work Sans
    weight: 500
spacing:
  base: 8px
  container-padding: 16px
  card-gap: 12px
shape:
  border-radius: 8px
---

# Kinetic Material Design System

## Vision

A high-energy, professional, and reliable visual language designed for field efficiency. It combines the urgency of high-contrast action colors (Deep Orange) with the clarity and calmness of utility tones (Cyan and Amber).

## Color Strategy

- **Primary (#ff4500)**: Used for high-priority actions like "Start New Survey" or "Finalize Inventory".
- **Secondary (#ff8c00)**: Used for secondary CTAs and status indicators requiring attention.
- **Tertiary (#00bcd4)**: Used for information highlights, badges, and positive "Done" states.
- **Surface Strategy**: A cool, slightly tinted neutral palette ensures the warm brand colors pop while maintaining a modern, professional feel.

## Typography

- **Headlines**: Bold Work Sans for immediate hierarchy and readability in variable lighting.
- **Body**: Clean, legible weights for inventory lists and data entry.
- **Monospace (Utility)**: Used for dimensions and technical data to ensure character alignment.

## Components

- **Cards**: Use `ROUND_EIGHT` (8px) corner radius. Elevation should be subtle (flat or low shadows) to keep the interface feeling "app-like" and lightweight.
- **Buttons**: Large touch targets (min 48dp height) with high-contrast text.
- **Inputs**: Outlined style with clear labels to accommodate agent notes and inventory details.
