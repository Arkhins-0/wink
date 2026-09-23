import type { Config } from "tailwindcss";

/**
 * Night with one gold accent: the CTR yellow on near-black. The same
 * palette as the Android app's Theme.kt, so the site and the app read as
 * one thing.
 */
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        night: { DEFAULT: "#0B0B0C", panel: "#161618", line: "#26262A" },
        snow: { DEFAULT: "#F4F4F5", soft: "#B4B4BA", faint: "#7A7A82" },
        gold: { DEFAULT: "#FFD100", deep: "#E0A800" },
        danger: "#FF5A5F",
      },
      fontFamily: {
        body: ["var(--font-body)", "system-ui", "sans-serif"],
      },
      boxShadow: {
        card: "0 1px 0 rgb(255 255 255 / 0.04) inset, 0 20px 50px -20px rgb(0 0 0 / 0.8)",
        glow: "0 0 80px -20px rgb(255 209 0 / 0.35)",
      },
    },
  },
  plugins: [],
};

export default config;
