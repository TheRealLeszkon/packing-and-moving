# SurveyAgent Implementation Specification (Jetpack Compose / Material 3)

## 1. Design System Tokens (Consistent Across All Screens)

### Typography (Work Sans)

- **Display Large**: Bold, 32sp, Line Height 40sp (Hello, Alex)
- **Headline Medium**: Bold, 24sp, Line Height 32sp (Screen Titles)
- **Title Large**: Bold, 18sp, Line Height 24sp (Card Titles)
- **Body Large**: Regular, 16sp, Line Height 24sp (Main Content)
- **Body Medium**: Regular, 14sp, Line Height 20sp (Subtext/Descriptions)
- **Label Large**: Medium, 14sp, Line Height 20sp (Buttons, Badges)

### Color Palette (Kinetic Material)

- **Primary**: #FF4500 (High-energy orange for main actions)
- **Secondary**: #FF8C00 (Amber for secondary actions/warnings)
- **Tertiary**: #00BCD4 (Cyan for info/success badges)
- **Surface**: #F9F9FF (Cool-tinted neutral background)
- **Surface Container**: #EEF1FF (Card/Section backgrounds)
- **On-Surface**: #191B23 (Deep navy/black text)
- **On-Surface Variant**: #43474E (Medium gray for secondary text)

### Elevation & Shapes

- **Corner Radius**: 8dp (ROUND_EIGHT) for cards, buttons, and inputs.
- **Elevation**:
  - Level 0: Background Surface.
  - Level 1: Cards (1dp shadow).
  - Level 2: FABs and Active Navigation (3dp shadow).

---

## 2. Reusable UI Components

### TopAppBar

- **Structure**: Small TopAppBar (Material 3).
- **Leading**: `IconButton` with `Icons.Default.Menu`.
- **Title**: Centered text "SurveyAgent" in Headline Medium (Primary color).
- **Trailing**: `CircleShape` image/avatar (40dp) with `BorderStroke(1.dp, Outline)`.

### BottomNavBar

- **Structure**: NavigationBar (Material 3).
- **Items**: 3 destinations (Home, Surveys, Settings).
- **Indicator**: Rounded rectangle for active state (Secondary Container color).
- **Icons**: Home (Home), Assignment (Surveys), Settings (Settings).

### PrimaryActionButton (Large)

- **Structure**: Button with `fillMaxWidth()` and `height(56.dp)`.
- **Colors**: Container (Primary), Content (OnPrimary).
- **Shape**: RoundedCornerShape(8.dp).

---

## 3. Screen Specifications

### 3.1 Home Screen (SCREEN_9)

- **Purpose**: Agent dashboard for daily assignments.
- **Hierarchy**: Column (Vertical) -> Scrollable.
- **Header**: Hello message (Display Large) + Subtext.
- **Action Section**: `PrimaryActionButton` "Start New Survey" with leading icon `Icons.Default.Add`.
- **Draft Section**: Card (Surface Container) containing "Draft in Progress" badge (Secondary color), Customer Name (Headline Small), and "Continue Survey" OutlinedButton.
- **Recent List**: LazyColumn of cards. Each card contains:
  - Customer Title + Status Badge (Tertiary "Done").
  - Nested Row for "Total Items" and "Est. Volume" using Monospace/Utility font for numbers.
- **Spacing**: 16dp horizontal margins; 24dp vertical spacing between major sections.

### 3.2 Create Survey Screen (SCREEN_7)

- **Purpose**: Intake form for new surveys.
- **Hierarchy**: Scaffold -> Column -> Scrollable.
- **App Bar**: TopAppBar with `Icons.Default.ArrowBack`.
- **Fields**: OutlinedTextFields (Customer Name, Phone, Email, Pickup, Destination, Notes).
  - **Styling**: Outlined style with 8dp radius. Placeholder text in On-Surface Variant. Notes field is multi-line (min 4 lines).
- **Footer**: Row (Fixed bottom) with "Cancel" (TextButton) and "Start Survey" (Button, Primary).
- **Keyboard**: Individual IME actions (Next) for all fields except Notes (Default).

### 3.3 Survey Camera Screen (SCREEN_5)

- **Purpose**: Professional camera interface for item capture.
- **Hierarchy**: Box (Full Screen).
- **Viewfinder**: Full-bleed background layer (Camera Preview).
- **Overlays**:
  - **Top Bar**: Transparent overlay with "SCAN ROOM INVENTORY" title and room metadata.
  - **Target Reticle**: Centered green corner brackets (Icon/Shape).
  - **Bottom Controls**: Black semi-transparent container.
    - Row: Gallery Shortcut (left), Capture Button (Large circular white button with inner ring), Flash Toggle (right).
    - Capture Item Button: Wide button below camera ring.
  - **Progress Bar**: LinearProgressIndicator at the very bottom with "Survey Progress" label and percentage.

### 3.4 AI Survey Report (SCREEN_2)

- **Purpose**: Review of AI-detected items.
- **Hierarchy**: LazyColumn with sticky header search bar.
- **Search**: OutlinedTextField with `Icons.Default.Search` leading icon.
- **Inventory Cards**:
  - **Structure**: Elevated Card with Row layout.
  - **Leading**: Image (64dp square, 8dp radius).
  - **Center**: Column with Item Name (Title Large) and Room (Body Medium).
  - **Trailing**: Status Icon (Check Circle for High Confidence, Error/Warning for Low Confidence).
- **Expand/Collapse**: Cards expand on click to show physical properties (Dimensions, Weight).
- **Floating Action**: FAB with `Icons.Default.Add` (Primary color).

### 3.5 Item Detail Screen (SCREEN_8)

- **Purpose**: Granular editing of item data.
- **Hierarchy**: LazyColumn.
- **Hero**: Box with Large Image + "95% Match" Badge overlay.
- **Gallery**: Horizontal Scrollable Row of 80dp thumbnails.
- **Form Sections**:
  - **Physical Properties**: 2x3 Grid of OutlinedTextFields (Length, Width, Height, Weight, Value).
  - **Handling**: Column of Checkboxes/Rows (Fragile, Needs Disassembly).
  - **Condition**: Large OutlinedTextField for free-text notes.
- **Sticky Footer**: Two buttons: "Save Changes" (Primary) and "Retake Photo" (Outlined, Cyan accent).

### 3.6 Final Survey Summary (SCREEN_6)

- **Purpose**: Final customer sign-off dashboard.
- **Hierarchy**: LazyColumn.
- **Customer Card**: Profile Avatar (Row) + Name/Contact + Origin/Destination addresses.
- **Stat Grid**: 2x2 grid of Surface Container cards showing Total Volume, Total Weight, Total Items, and Warnings.
- **Special Requirements**: FlowRow of chips/badges (Fragile, Disassembly, Special Handling).
- **Actions**: Row of small OutlinedButtons (Save, Export, Send) + Large "Complete Survey" Primary Button.

---

## 4. Interactions & System Behavior

- **Navigation**: Sliding transitions between screens. Fade-in for camera overlay elements.
- **Dark Mode**: All Surface colors invert to deep navy (#121212); Surface Containers to #1E1E1E. Primary and Secondary colors remain high-vibrancy.
- **Touch**: Ripple effects on all cards and buttons. Minimum 48dp touch targets for all interactive elements.
- **Scroll**: Overscroll effect enabled. Sticky headers for "Recent Surveys" and "AI Report" categories.
