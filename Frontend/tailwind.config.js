/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        aurora: {
          blue: '#1f65ff',
          purple: '#b376eb',
          pink: '#f167a5',
          dark: '#0a0a1a'
        }
      },
      backgroundImage: {
        'aurora-gradient': 'linear-gradient(135deg, #0a0a1a 0%, #1f1f3a 50%, #2d1a3a 100%)',
      }
    },
  },
  plugins: [],
}
