/** @type {import('tailwindcss').Config} */
// Colors mirror composeApp/.../ui/theme/Color.kt (LightColorScheme). The existing web screens use the
// `indigo` (primary) and `slate` (neutral) scales, so those are remapped to the Android blue / gray
// scales; every screen picks up the Android look without per-file edits.
const blue = {
  50: "#EFF6FF", 100: "#DBEAFE", 200: "#BFDBFE", 300: "#93C5FD", 400: "#60A5FA",
  500: "#3B82F6", 600: "#3B82F6", 700: "#2563EB", 800: "#1E3A5F", 900: "#1E3A5F", 950: "#172554"
};
const gray = {
  50: "#F9FAFB", 100: "#F3F4F6", 200: "#E5E7EB", 300: "#D1D5DB", 400: "#9CA3AF",
  500: "#6B7280", 600: "#4B5563", 700: "#374151", 800: "#1F2937", 900: "#111827", 950: "#030712"
};
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        indigo: blue,
        slate: gray,
        md: {
          primary: "#3B82F6", primaryContainer: "#DBEAFE", onPrimaryContainer: "#1E3A5F",
          secondary: "#10B981", secondaryContainer: "#D1FAE5", onSecondaryContainer: "#064E3B",
          tertiary: "#F59E0B", tertiaryContainer: "#FEF3C7", onTertiaryContainer: "#78350F",
          error: "#DC2626", errorContainer: "#FEF2F2", onErrorContainer: "#7F1D1D",
          background: "#F9FAFB", surface: "#FFFFFF", surfaceVariant: "#F3F4F6",
          onSurface: "#111827", onSurfaceVariant: "#4B5563",
          outline: "#D1D5DB", outlineVariant: "#E5E7EB"
        }
      },
      // Shapes.kt: 4 / 8 / 12 / 16 / 24 dp
      borderRadius: { md3xs: "4px", md3s: "8px", md3m: "12px", md3l: "16px", md3xl: "24px" }
    }
  },
  plugins: []
};
