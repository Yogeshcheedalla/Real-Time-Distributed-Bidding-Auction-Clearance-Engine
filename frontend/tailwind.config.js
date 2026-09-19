/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#070b14', panel: '#0b1120', line: '#1e293b',
        acc: '#22d3ee', acc2: '#818cf8', good: '#34d399', warn: '#fbbf24', bad: '#f87171',
      },
      fontFamily: {
        // Professional web stack — no AI/vendor fonts (explicit project requirement)
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
