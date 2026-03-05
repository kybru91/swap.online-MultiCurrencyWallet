/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./App.{js,jsx,ts,tsx}', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#3B82F6',
        'primary-dark': '#1D4ED8',
        surface: '#1F2937',
        background: '#111827',
        'text-primary': '#F9FAFB',
        'text-secondary': '#9CA3AF',
        success: '#10B981',
        error: '#EF4444',
        warning: '#F59E0B',
      },
    },
  },
  plugins: [],
};
