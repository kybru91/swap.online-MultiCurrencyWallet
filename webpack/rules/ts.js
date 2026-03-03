export default [
  {
    test: /\.tsx?$/, // ts, tsx
    use: [
      {
        loader: 'ts-loader',
        options: { transpileOnly: true },
      },
    ],
    exclude: /node_modules/,
  },
]
