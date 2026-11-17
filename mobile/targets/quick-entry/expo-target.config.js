/** @type {import('@bacons/apple-targets/app.plugin').Config} */
module.exports = {
  type: "widget",
  name: "QuickEntry",
  displayName: "Дневник",
  bundleIdentifier: ".widget",
  deploymentTarget: "18.0",
  colors: {
    $accent: "#12908C",
    $widgetBackground: "#12908C",
    gradientStart: { light: "#2FC9BC", dark: "#0E3B48" },
    gradientEnd: { light: "#7C5CE8", dark: "#3D2A82" },
  },
};
