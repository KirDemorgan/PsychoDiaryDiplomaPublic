const fs = require("fs");
const path = require("path");
const { IOSConfig, withDangerousMod, withXcodeProject } = require("expo/config-plugins");

const INTENT_FILE = "QuickEntryIntent.swift";
const INTENT_SOURCE = path.join("targets", "quick-entry", INTENT_FILE);

const APP_ONLY_FLAG = "QUICK_ENTRY_APP";

function enableAppOnlyCode(project) {
  const { firstTarget } = project.getFirstTarget();
  const configurations = IOSConfig.XcodeUtils.getBuildConfigurationsForListId(project, firstTarget.buildConfigurationList);
  for (const [, configuration] of configurations) {
    const current = String(configuration.buildSettings.SWIFT_ACTIVE_COMPILATION_CONDITIONS ?? "$(inherited)").replace(/^"|"$/g, "");
    if (!current.split(" ").includes(APP_ONLY_FLAG)) {
      configuration.buildSettings.SWIFT_ACTIVE_COMPILATION_CONDITIONS = `"${current} ${APP_ONLY_FLAG}"`;
    }
  }
}

/**
 * App Intents metadata for a control action is resolved from the app bundle,
 * so the intent shared with the widget extension must also be compiled into the app target.
 */
module.exports = function withQuickEntryIntent(config) {
  config = withDangerousMod(config, [
    "ios",
    (modConfig) => {
      const { projectRoot, platformProjectRoot, projectName } = modConfig.modRequest;
      const destination = path.join(platformProjectRoot, projectName, INTENT_FILE);
      fs.copyFileSync(path.join(projectRoot, INTENT_SOURCE), destination);
      return modConfig;
    },
  ]);

  return withXcodeProject(config, (modConfig) => {
    const { projectName } = modConfig.modRequest;
    const project = modConfig.modResults;
    IOSConfig.XcodeUtils.addBuildSourceFileToGroup({
      filepath: path.join(projectName, INTENT_FILE),
      groupName: projectName,
      project,
    });
    enableAppOnlyCode(project);
    return modConfig;
  });
};
