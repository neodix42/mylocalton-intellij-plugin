package org.ton.mylocalton.plugin;

import static java.util.Objects.isNull;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.ton.ton4j.liteclient.LiteClient;
import org.ton.ton4j.liteclient.LiteClientParser;
import org.ton.ton4j.liteclient.api.ResultLastBlock;

/** Factory for creating the Demo Tool Window. */
public class MyLocalTonToolWindowFactory implements ToolWindowFactory {
  // Use static logger to avoid initialization issues
  private static final Logger LOG = Logger.getInstance(MyLocalTonToolWindowFactory.class);
  private JLabel statusLabel;
  private JLabel infoLabel; // Label to show "absolute path was copied" message
  private JLabel versionLabel; // Label to show version information
  private Timer lockFileMonitor;
  private JButton startButton;
  private JButton stopButton;
  private JButton resetButton;
  private JButton deleteButton;
  private JLabel messageLabel;
  private JPanel startupSettingsPanel;
  private JCheckBox testnetCheckbox; // Reference to the testnet checkbox
  private JButton downloadButton;
  private Process process;
  ScheduledExecutorService monitorExecutorService;
  // References to startup settings checkboxes and combobox
  private JCheckBox tonHttpApiV2;
  private JCheckBox webExplorer;
  private JCheckBox dataGenerator;
  private JCheckBox noGuiMode;
  private JCheckBox debugMode;
  private JComboBox<Integer> validators;
  LiteClient liteClient;

  /**
   * Checks if the myLocalTon.lock file exists in the user.dir directory.
   *
   * @return true if the lock file exists, false otherwise
   */
  private boolean isLockFileExists() {
    String userDir = System.getProperty("user.home");
    Path lockFilePath = Paths.get(userDir, "myLocalTon.lock");
    return Files.exists(lockFilePath);
  }

  // Flags to track states
  private boolean isDownloadInProgress = false;
  private boolean isProcessRunning = false; // Flag to track if the process is running

  /**
   * Updates the status label, button states, and panel states based on the lock file existence and
   * JAR file existence.
   */
  private void updateStatusLabel() {
    boolean lockExists = isLockFileExists();
    boolean jarExists = checkIfJarExists();

    // Update isProcessRunning flag based on lock file existence
    if (lockExists) {
      isProcessRunning = true;
    } else {
      isProcessRunning = false;
    }

    if (statusLabel != null) {
      if (lockExists) {
        statusLabel.setText("Status: running");
      } else {
        statusLabel.setText("Status: not running");
      }
    }

    // Update button states based on lock file existence
    if (startButton != null) {
      startButton.setEnabled(
          !lockExists
              && jarExists
              && !isDownloadInProgress); // Disable Start when lock exists, no JAR exists, or
      // download in progress
    }

    if (stopButton != null) {
      stopButton.setEnabled(
          lockExists
              && !isDownloadInProgress); // Disable Stop when lock doesn't exist or download in
      // progress
    }

    // Disable/enable the startup settings panel based on lock file existence, JAR existence, and
    // download status
    if (startupSettingsPanel != null) {
      boolean shouldEnable =
          !lockExists
              && !isProcessRunning
              && jarExists
              && !isDownloadInProgress; // Disable when lock exists, process is running, no JAR
      // exists, or download in progress
      startupSettingsPanel.setEnabled(shouldEnable);

      // Recursively disable/enable all components inside the panel
      setEnabledRecursively(startupSettingsPanel, shouldEnable);

      if (resetButton != null) {
        resetButton.setEnabled(!isProcessRunning);
      }
      if (deleteButton != null) {
        deleteButton.setEnabled(!isProcessRunning);
      }
    }
  }

  /**
   * Checks if any JAR file exists (both mainnet and testnet versions for both architectures).
   *
   * @return true if any JAR file exists, false otherwise
   */
  private boolean checkIfJarExists() {
    Path downloadDir = Paths.get(System.getProperty("user.home"), ".mylocalton");

    // Check for mainnet JAR files
    Path mainnetX86JarPath = downloadDir.resolve("MyLocalTon-x86-64.jar");
    Path mainnetArmJarPath = downloadDir.resolve("MyLocalTon-arm64.jar");

    // Check for testnet JAR files
    Path testnetX86JarPath = downloadDir.resolve("MyLocalTon-x86-64-testnet.jar");
    Path testnetArmJarPath = downloadDir.resolve("MyLocalTon-arm64-testnet.jar");

    // Check if any JAR file exists
    return Files.exists(mainnetX86JarPath)
        || Files.exists(mainnetArmJarPath)
        || Files.exists(testnetX86JarPath)
        || Files.exists(testnetArmJarPath);
  }

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    LOG.warn("Creating tool window content for project: " + project.getName());

    try {
      // Create main panel with vertical BoxLayout
      JPanel mainPanel = new JPanel();
      mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

      // 1. Installation Section
      JPanel installationPanel = createInstallationPanel(project);
      installationPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // Top align
      installationPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160)); // Fixed height
      mainPanel.add(installationPanel);
      mainPanel.add(Box.createVerticalStrut(5)); // Reduced spacing for compactness

      // 2. Startup settings Section
      startupSettingsPanel = createStartupSettingsPanel(project);
      startupSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // Top align
      startupSettingsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160)); // Fixed height
      mainPanel.add(startupSettingsPanel);
      mainPanel.add(Box.createVerticalStrut(5)); // Reduced spacing for compactness

      // 3. Actions Section
      JPanel actionsPanel = createActionsPanel(project);
      actionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // Top align
      actionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200)); // Fixed height
      mainPanel.add(actionsPanel);
      mainPanel.add(Box.createVerticalStrut(5)); // Reduced spacing for compactness

      // 4. Uninstall Section
      JPanel uninstallPanel = createUninstallPanel(project);
      uninstallPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // Top align
      uninstallPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120)); // Fixed height
      mainPanel.add(uninstallPanel);

      // Add the panel to the tool window
      ContentFactory contentFactory = ContentFactory.getInstance();
      Content content = contentFactory.createContent(mainPanel, "MyLocalTon", false);
      toolWindow.getContentManager().addContent(content);

      monitorExecutorService = Executors.newSingleThreadScheduledExecutor();
      monitorExecutorService.scheduleWithFixedDelay(
          () -> {
            Thread.currentThread().setName("MyLocalTon-Plugin - Blockchain Monitor");

            try {
              String userHomeDir = System.getProperty("user.home");
              String last = "";
              if (isLockFileExists()) {
                if (isNull(liteClient)) {
                  if (Files.exists(Paths.get(getLiteClientPath(userHomeDir)))) {
                    liteClient =
                        LiteClient.builder()
                            .pathToGlobalConfig(getGlobalConfigPath(userHomeDir))
                            .pathToLiteClientBinary(getLiteClientPath(userHomeDir))
                            .build();
                  }
                }

                //            String size =
                // getDirectorySizeUsingDu(getMyLocalTonPath(userHomeDir));

                last = liteClient.executeLast();
              }
              if (last.contains("latest masterchain block known to server")) {
                ResultLastBlock resultLastBlock = LiteClientParser.parseLast(last);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);

                // Make sure the startup panel is disabled when the process is running
                if (startupSettingsPanel != null) {
                  startupSettingsPanel.setEnabled(false);
                  setEnabledRecursively(startupSettingsPanel, false);
                }

                //                          java.util.List<ResultLastBlock> shards =
                // LiteClientParser.parseAllShards(liteClient.executeAllshards(resultLastBlock));
                //                        LOG.warn("size last shards "+ size+" "+
                // resultLastBlock.getSeqno()+" "+shards.size());
                statusLabel.setText("Block: " + resultLastBlock.getSeqno());
              } else {
                updateStatusLabel();
              }
            } catch (Exception ex) {
              // Don't call updateStatusLabel() directly as it might re-enable the panel
              // Instead, check if the process is running first
              if (isProcessRunning) {
                // If process is running, ensure the startup panel stays disabled
                if (startupSettingsPanel != null) {
                  startupSettingsPanel.setEnabled(false);
                  setEnabledRecursively(startupSettingsPanel, false);
                }
              } else {
                // Only update the status label if the process is not running
                updateStatusLabel();
              }
            }
          },
          2L,
          2L,
          TimeUnit.SECONDS);

      LOG.warn("Tool window content created successfully");

    } catch (Exception e) {
      LOG.warn("Error creating tool window content: " + e.getMessage(), e);
    }
  }

  /**
   * Determines if the system is running on ARM architecture.
   *
   * @return true if running on ARM architecture, false otherwise (likely x86-64)
   */
  private boolean isArmArchitecture() {
    String arch = System.getProperty("os.arch").toLowerCase();
    return arch.contains("arm") || arch.contains("aarch");
  }

  /**
   * Gets the appropriate JAR filename based on architecture and testnet selection.
   *
   * @param isTestnet Whether testnet is selected
   * @return The appropriate JAR filename
   */
  private String getJarFilename(boolean isTestnet) {
    boolean isArm = isArmArchitecture();

    if (isArm) {
      return isTestnet ? "MyLocalTon-arm64-testnet.jar" : "MyLocalTon-arm64.jar";
    } else {
      return isTestnet ? "MyLocalTon-x86-64-testnet.jar" : "MyLocalTon-x86-64.jar";
    }
  }

  /**
   * Gets the download URL for the JAR file based on architecture and testnet selection.
   *
   * @param isTestnet Whether testnet is selected
   * @return The URL to download the JAR file from
   */
  private String getDownloadUrl(boolean isTestnet) {
    String baseUrl = "https://github.com/neodix42/mylocalton/releases/latest/download/";
    return baseUrl + getJarFilename(isTestnet);
  }

  private JPanel createInstallationPanel(Project project) {
    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Installation",
            TitledBorder.LEFT,
            TitledBorder.TOP));

    // Create version label with a distinct appearance to ensure visibility
    versionLabel = new JLabel(" ");
    versionLabel.setOpaque(true); // Make it opaque

    // Add the version label directly to the panel's NORTH-EAST area with minimal height
    JPanel versionPanel =
        new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); // Reduced vertical padding
    versionPanel.add(versionLabel);
    panel.add(versionPanel, BorderLayout.NORTH);

    downloadButton = new JButton();
    // Create testnet checkbox
    testnetCheckbox = new JCheckBox("Testnet");
    testnetCheckbox.setToolTipText(
        "Download MyLocalTon based on TON binaries from testnet branch.");

    // Check if any JAR file exists (both mainnet and testnet versions for both architectures)
    Path downloadDir = Paths.get(System.getProperty("user.home"), ".mylocalton");

    // Check for mainnet JAR files
    Path mainnetX86JarPath = downloadDir.resolve("MyLocalTon-x86-64.jar");
    Path mainnetArmJarPath = downloadDir.resolve("MyLocalTon-arm64.jar");

    // Check for testnet JAR files
    Path testnetX86JarPath = downloadDir.resolve("MyLocalTon-x86-64-testnet.jar");
    Path testnetArmJarPath = downloadDir.resolve("MyLocalTon-arm64-testnet.jar");

    // Check if any JAR file exists
    boolean mainnetJarExists = Files.exists(mainnetX86JarPath) || Files.exists(mainnetArmJarPath);
    boolean testnetJarExists = Files.exists(testnetX86JarPath) || Files.exists(testnetArmJarPath);
    boolean jarExists = mainnetJarExists || testnetJarExists;

    // If a testnet JAR exists, select the testnet checkbox
    if (testnetJarExists) {
      testnetCheckbox.setSelected(true);
    }

    // If any JAR file exists, execute it with "version" parameter to get the version
    if (jarExists) {
      new Thread(
              () -> {
                try {
                  // Determine which JAR file to use
                  Path jarPath;
                  if (testnetJarExists) {
                    jarPath =
                        testnetCheckbox.isSelected()
                            ? (Files.exists(testnetArmJarPath)
                                ? testnetArmJarPath
                                : testnetX86JarPath)
                            : (Files.exists(mainnetArmJarPath)
                                ? mainnetArmJarPath
                                : mainnetX86JarPath);
                  } else {
                    jarPath =
                        Files.exists(mainnetArmJarPath) ? mainnetArmJarPath : mainnetX86JarPath;
                  }

                  // Trim the output and update the version label
                  final String version = getMyLocalTonVersion(jarPath.toString());

                  // Update the version label in the UI thread
                  SwingUtilities.invokeLater(
                      () -> {
                        versionLabel.setText(version);
                        versionLabel.repaint(); // Force repaint
                      });
                } catch (Exception ex) {
                  LOG.warn("Error getting version on startup: " + ex.getMessage(), ex);
                }
              })
          .start();
    }

    // Create download panel with centered Download button and progress bar
    JPanel downloadPanel = new JPanel();
    downloadPanel.setLayout(new BoxLayout(downloadPanel, BoxLayout.Y_AXIS));

    // Download button panel (centered)
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

    // Progress bar panel (centered) with visible border
    JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    progressPanel.setVisible(true); // Make sure the panel is visible
    progressPanel.setOpaque(true);
    progressPanel.setPreferredSize(new Dimension(140, 30)); // Ensure panel has size

    // Create progress bar (initially invisible with larger size)
    JProgressBar progressBar = new JProgressBar(0, 100);
    progressBar.setValue(0);
    progressBar.setStringPainted(true);
    progressBar.setPreferredSize(new Dimension(140, 20)); // Larger size for better visibility
    progressBar.setVisible(false); // Initially invisible to avoid gray appearance
    progressBar.setOpaque(true); // Make sure background is visible
    progressBar.setBackground(progressPanel.getBackground()); // Match panel background exactly
    progressBar.setBorderPainted(false); // Remove border to eliminate gray edges

    progressPanel.add(progressBar);

    // Download button - set initial state based on JAR existence
    downloadButton.setText(jarExists ? "DOWNLOADED" : "DOWNLOAD");
    downloadButton.setEnabled(!jarExists); // Disable if JAR exists
    testnetCheckbox.setEnabled(!jarExists);
    downloadButton.setPreferredSize(new Dimension(150, 30));
    downloadButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn("Download button clicked");

            String ver = getJavaVersion();
            LOG.warn("Java Version detected: " + ver);

            if (extractJavaMajorVersion(ver) < 21) {
              SwingUtilities.invokeLater(
                  () ->
                      Messages.showInfoMessage(
                          project,
                          "Old Java version detected ("
                              + extractJavaMajorVersion(ver)
                              + "). MyLocalTon Plugin requires Java 21 or higher.",
                          "MyLocalTon Plugin"));
              return;
            }

            // Get testnet checkbox state
            boolean isTestnet = testnetCheckbox.isSelected();

            // Make progress bar visible when Download button is clicked
            progressBar.setVisible(true);
            progressPanel.setVisible(
                true); // Make sure the panel containing the progress bar is visible too

            // Force UI update to ensure progress bar is visible
            progressPanel.revalidate();
            progressPanel.repaint();

            // Set download in progress flag
            isDownloadInProgress = true;

            // Disable the download button during download
            downloadButton.setEnabled(false);
            testnetCheckbox.setEnabled(false);

            // Disable startup settings panel and actions panel during download
            if (startupSettingsPanel != null) {
              startupSettingsPanel.setEnabled(false);
              setEnabledRecursively(startupSettingsPanel, false);
            }

            // Disable actions panel (index 2 in the main panel)
            Container mainPanel = panel.getParent();
            if (mainPanel != null && mainPanel.getComponentCount() > 2) {
              Component actionsPanel = mainPanel.getComponent(2);
              if (actionsPanel instanceof JPanel) {
                actionsPanel.setEnabled(false);
                setEnabledRecursively((Container) actionsPanel, false);
              }
            }

            // Create a thread for downloading the file
            new Thread(
                    () -> {
                      try {
                        // URL of the file to download based on architecture and testnet selection
                        String fileUrl = getDownloadUrl(isTestnet);
                        LOG.warn("Downloading from URL: " + fileUrl);

                        // Create a directory for the download if it doesn't exist
                        Path downloadDir =
                            Paths.get(System.getProperty("user.home"), ".mylocalton");
                        if (!Files.exists(downloadDir)) {
                          Files.createDirectories(downloadDir);
                        }

                        // Path where the file will be saved
                        String jarFilename = getJarFilename(isTestnet);
                        Path targetPath = downloadDir.resolve(jarFilename);
                        File targetFile = targetPath.toFile();

                        // Download the file and update progress
                        downloadFile(fileUrl, targetFile, progressBar);

                        // Show success message
                        SwingUtilities.invokeLater(
                            () -> {
                              // Reset download in progress flag
                              isDownloadInProgress = false;

                              // Change download button text and keep it disabled
                              downloadButton.setText("DOWNLOADED");
                              downloadButton.setEnabled(false);
                              testnetCheckbox.setEnabled(false);

                              // Make sure the progress bar is hidden
                              progressBar.setVisible(false);

                              // Get the bottom panel to add the "Open Location" link
                              JPanel bottomPanel =
                                  (JPanel)
                                      panel.getComponent(
                                          2); // Get the bottom panel (SOUTH component)

                              // Clear the bottom panel and recreate it
                              bottomPanel.removeAll();

                              // Recreate the bottom panel with the same BoxLayout
                              bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

                              // Add "Open Location" link to the left side
                              JLabel openLocationLink = createLink("Open Location", project, null);
                              openLocationLink.addMouseListener(
                                  new MouseAdapter() {
                                    @Override
                                    public void mouseClicked(MouseEvent e) {
                                      LOG.warn("Open Location link clicked");
                                      try {
                                        // Open file explorer at the download location
                                        String dirPath = downloadDir.toString();
                                        if (Desktop.isDesktopSupported()) {
                                          Desktop.getDesktop().open(new File(dirPath));
                                        } else {
                                          SwingUtilities.invokeLater(
                                              () ->
                                                  Messages.showInfoMessage(
                                                      project,
                                                      "Download location: " + dirPath,
                                                      "MyLocalTon Plugin"));
                                        }
                                      } catch (Exception ex) {
                                        LOG.warn(
                                            "Error opening download location: " + ex.getMessage(),
                                            ex);
                                        SwingUtilities.invokeLater(
                                            () ->
                                                Messages.showErrorDialog(
                                                    project,
                                                    "Error opening download location: "
                                                        + ex.getMessage(),
                                                    "MyLocalTon Plugin"));
                                      }
                                    }
                                  });
                              bottomPanel.add(openLocationLink);

                              // Add flexible space to push the checkbox to the right
                              bottomPanel.add(Box.createHorizontalGlue());

                              // Add Testnet checkbox on the right
                              bottomPanel.add(testnetCheckbox);

                              // Add padding around the panel
                              bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

                              bottomPanel.revalidate();
                              bottomPanel.repaint();

                              // Now that download is successful, update the status label and enable
                              // buttons
                              updateStatusLabel();

                              final String version = getMyLocalTonVersion(targetPath.toString());
                              LOG.warn("MyLocalTon Version detected: " + version);
                              if (StringUtils.isEmpty(version)) {
                                SwingUtilities.invokeLater(
                                    () ->
                                        Messages.showWarningDialog(
                                            project,
                                            "Download completed successfully, but MyLocalTon version cannot be detected!\nMyLocalTon requires Java 21+\nDetected Java version: "
                                                + getJavaVersion()
                                                + "\nFile saved to: "
                                                + targetPath,
                                            "MyLocalTon Plugin"));
                                // Update the version label in the UI thread
                                SwingUtilities.invokeLater(
                                    () -> {
                                      versionLabel.setText(version);
                                    });
                              } else {
                                SwingUtilities.invokeLater(
                                    () ->
                                        Messages.showInfoMessage(
                                            project,
                                            "Download completed successfully!\nFile saved to: "
                                                + targetPath,
                                            "MyLocalTon Plugin"));
                              }
                            });
                      } catch (Exception ex) {
                        LOG.warn("Error downloading file: " + ex.getMessage(), ex);
                        SwingUtilities.invokeLater(
                            () -> {
                              // Reset download in progress flag
                              isDownloadInProgress = false;

                              progressBar.setVisible(false);

                              // Get the bottom panel to add the "Download failed" label
                              JPanel bottomPanel =
                                  (JPanel) panel.getComponent(1); // Get the bottom panel

                              // Clear the bottom panel and recreate it
                              bottomPanel.removeAll();

                              // Add "Download failed" label to the left side
                              JLabel failedLabel = new JLabel("Download failed.");

                              // Recreate the bottom panel with the same BoxLayout
                              bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
                              bottomPanel.add(failedLabel);

                              // Add flexible space to push the checkbox to the right
                              bottomPanel.add(Box.createHorizontalGlue());

                              // Add Testnet checkbox on the right
                              bottomPanel.add(testnetCheckbox);

                              // Add padding around the panel
                              bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

                              bottomPanel.revalidate();
                              bottomPanel.repaint();

                              SwingUtilities.invokeLater(
                                  () ->
                                      Messages.showErrorDialog(
                                          project,
                                          "Error downloading file: " + ex.getMessage(),
                                          "MyLocalTon Plugin"));
                              // Keep the download button disabled but change text back to original
                              downloadButton.setText("DOWNLOAD");
                              downloadButton.setEnabled(true);
                              testnetCheckbox.setEnabled(true);
                            });
                      }
                    })
                .start();
          }
        });
    buttonPanel.add(downloadButton);

    // Add components to download panel with no spacing
    downloadPanel.add(buttonPanel);
    // No vertical spacing between button and progress bar
    downloadPanel.add(progressPanel);

    panel.add(downloadPanel, BorderLayout.CENTER);

    // Create bottom panel with BoxLayout for horizontal alignment
    JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

    // Add left component (Open Location link)
    if (jarExists) {
      // If JAR exists, show the "Open Location" link
      JLabel openLocationLink = createLink("Open Location", project, null);
      openLocationLink.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              LOG.warn("Open Location link clicked");
              try {
                // Open file explorer at the download location
                String dirPath = downloadDir.toString();
                if (Desktop.isDesktopSupported()) {
                  Desktop.getDesktop().open(new File(dirPath));
                } else {
                  // Fallback for systems where Desktop is not supported
                  SwingUtilities.invokeLater(
                      () ->
                          Messages.showInfoMessage(
                              project, "Download location: " + dirPath, "MyLocalTon Plugin"));
                }
              } catch (Exception ex) {
                LOG.warn("Error opening download location: " + ex.getMessage(), ex);
                SwingUtilities.invokeLater(
                    () ->
                        Messages.showErrorDialog(
                            project,
                            "Error opening download location: " + ex.getMessage(),
                            "MyLocalTon Plugin"));
              }
            }
          });
      bottomPanel.add(openLocationLink);
      // Add some horizontal padding
      bottomPanel.add(Box.createHorizontalStrut(5));
    }

    // Add flexible space to push the checkbox to the right
    bottomPanel.add(Box.createHorizontalGlue());

    // Add Testnet checkbox on the right
    bottomPanel.add(testnetCheckbox);

    // Add padding around the panel
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

    panel.add(bottomPanel, BorderLayout.SOUTH);

    return panel;
  }

  private static @NotNull String executeProcess(String command) {
    LOG.warn("Executing command: " + command);

    try {
      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.directory(Paths.get(System.getProperty("user.home"), ".mylocalton").toFile());
      if (SystemUtils.IS_OS_WINDOWS) {
        processBuilder.command("cmd.exe", "/c", "\"" + command + "\"");
      } else {
        processBuilder.command("sh", "-c", command);
      }
      // Redirect error output to NUL to suppress the error messages
      processBuilder.redirectError(
          ProcessBuilder.Redirect.to(new File(SystemUtils.IS_OS_WINDOWS ? "NUL" : "/dev/null")));
      Process process = processBuilder.start();
      String processOutput = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
      process.waitFor();

      return processOutput.trim();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Loads settings from the settings.json file if it exists and sets the checkboxes accordingly.
   */
  private void loadSettingsFromFile() {
    String userHome = System.getProperty("user.home");
    String osName = System.getProperty("os.name").toLowerCase();
    String settingsFilePath;

    // Determine the appropriate path based on OS
    if (osName.contains("win")) {
      settingsFilePath = userHome + "\\.mylocalton\\myLocalTon\\settings.json";
    } else {
      // For Linux and macOS
      settingsFilePath = userHome + "/.mylocalton/myLocalTon/settings.json";
    }

    File settingsFile = new File(settingsFilePath);
    if (settingsFile.exists()) {
      LOG.warn("Settings file found at: " + settingsFilePath);

      // Use a more robust approach to read the settings file
      try {
        // Read the file content as a string
        String jsonContent = new String(Files.readAllBytes(settingsFile.toPath()));

        // Check if the file contains the keys we're looking for
        boolean enableTonHttpApi = jsonContent.contains("\"enableTonHttpApi\": true");
        boolean enableBlockchainExplorer =
            jsonContent.contains("\"enableBlockchainExplorer\": true");
        boolean enableDataGenerator = jsonContent.contains("\"enableDataGenerator\": true");

        // Set checkboxes based on settings
        if (tonHttpApiV2 != null) {
          tonHttpApiV2.setSelected(enableTonHttpApi);
        }

        if (webExplorer != null) {
          webExplorer.setSelected(enableBlockchainExplorer);
        }

        if (dataGenerator != null) {
          dataGenerator.setSelected(enableDataGenerator);
        }

        LOG.warn("Settings loaded successfully from file");
      } catch (IOException e) {
        LOG.warn("Error loading settings from file: " + e.getMessage(), e);
      }
    } else {
      LOG.warn("Settings file not found at: " + settingsFilePath);
    }
  }

  private JPanel createStartupSettingsPanel(Project project) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Startup settings",
            TitledBorder.LEFT,
            TitledBorder.TOP));

    // Create a panel with BoxLayout for vertical arrangement
    JPanel contentPanel = new JPanel();
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

    // Create a grid layout for checkboxes
    JPanel checkboxPanel = new JPanel(new GridLayout(0, 2, 10, 2));

    // Initialize the checkboxes and combobox as class variables
    tonHttpApiV2 = new JCheckBox("TON HTTP API v2");
    tonHttpApiV2.setToolTipText(
        "Enables ton-http-api service on start. Install it manually first. Refer to github.com/neodix42/mylocalton");
    webExplorer = new JCheckBox("Web explorer");
    webExplorer.setToolTipText("Enables native TON blockchain web explorer on start.");
    dataGenerator = new JCheckBox("Data generator");
    dataGenerator.setToolTipText("Enables dummy data-generator on start.");
    noGuiMode = new JCheckBox("No GUI mode");
    noGuiMode.setToolTipText("Launches MyLocalTon without GUI.");

    // Create a panel for the listbox and label to be placed below "No GUI mode"
    JPanel listboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    // Create a listbox (JComboBox) with numbers 0 to 5
    Integer[] numbers = {0, 1, 2, 3, 4, 5};
    validators = new JComboBox<>(numbers);

    // Make the combobox width two times smaller
    Dimension comboBoxSize = validators.getPreferredSize();
    comboBoxSize.width = comboBoxSize.width - 20;
    validators.setPreferredSize(comboBoxSize);

    // Add the "add validators" label next to the listbox
    JLabel validatorsLabel = new JLabel("Validators:");
    listboxPanel.add(validatorsLabel);

    // Add the listbox to the panel
    listboxPanel.add(validators);

    debugMode = new JCheckBox("Debug mode");
    debugMode.setToolTipText(
        "Launches MyLocalTon in debug mode that add lots of useful information into log files.");

    checkboxPanel.add(tonHttpApiV2);
    checkboxPanel.add(webExplorer);
    checkboxPanel.add(dataGenerator);
    checkboxPanel.add(noGuiMode);
    checkboxPanel.add(listboxPanel);
    checkboxPanel.add(debugMode);

    contentPanel.add(checkboxPanel);
    panel.add(contentPanel, BorderLayout.CENTER);

    // Load settings from file if it exists
    loadSettingsFromFile();

    return panel;
  }

  private JPanel createActionsPanel(Project project) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Actions",
            TitledBorder.LEFT,
            TitledBorder.TOP));

    // Create buttons panel with vertical layout
    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));

    // Create buttons
    startButton = new JButton("Start");
    startButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn("Start button clicked");

            try {
              String userHomeDir = System.getProperty("user.home");
              // Get the path to the downloaded JAR file
              Path downloadDir = Paths.get(userHomeDir, ".mylocalton");

              // Check for any of the possible JAR files
              String jarFilename = getJarFilename(false); // Try mainnet first
              Path jarPath = downloadDir.resolve(jarFilename);

              if (!Files.exists(jarPath)) {
                // Try testnet version
                jarFilename = getJarFilename(true);
                jarPath = downloadDir.resolve(jarFilename);
              }

              if (!Files.exists(jarPath)) {
                SwingUtilities.invokeLater(
                    () ->
                        Messages.showErrorDialog(
                            project,
                            "MyLocalTon JAR file not found. Please download it first.",
                            "MyLocalTon Plugin"));
                return;
              }

              final String version = getMyLocalTonVersion(jarPath.toString());
              LOG.warn("MyLocalTon Version detected: " + version);
              if (StringUtils.isEmpty(version)) {
                SwingUtilities.invokeLater(
                        () ->
                                Messages.showWarningDialog(
                                        project,
                                        "MyLocalTon requires Java 21+\nDetected Java version: "
                                                + extractJavaMajorVersion(getJavaVersion()),
                                        "MyLocalTon Plugin"));
                return;
              }

              // Set the process running flag to true
              isProcessRunning = true;

              // Disable the startup panel when start button is clicked
              if (startupSettingsPanel != null) {
                startupSettingsPanel.setEnabled(false);
                setEnabledRecursively(startupSettingsPanel, false);
              }

              // Disable start button and enable stop button
              startButton.setEnabled(false);
              stopButton.setEnabled(true);

              resetButton.setEnabled(false);
              deleteButton.setEnabled(false);

              // Build the command with parameters based on checkbox states
              StringBuilder command = new StringBuilder();
              command
                  .append("\"")
                  .append(getJavaPath())
                  .append("\" -jar \"")
                  .append(jarPath)
                  .append("\"");

              // Add parameters based on checkbox states
              if (tonHttpApiV2.isSelected()) {
                command.append(" ton-http-api");
              }
              if (webExplorer.isSelected()) {
                command.append(" explorer");
              }
              if (dataGenerator.isSelected()) {
                command.append(" data-generator");
              }
              if (noGuiMode.isSelected()) {
                command.append(" nogui");
              }
              if (debugMode.isSelected()) {
                command.append(" debug");
              }

              // Add validators parameter if not 0
              int validatorsCount = (Integer) validators.getSelectedItem();
              if (validatorsCount > 0) {
                command.append(" with-validators-").append(validatorsCount);
              }

              // Launch the process in a detached way without console window
              String osName = System.getProperty("os.name").toLowerCase();

              // Create a ProcessBuilder for launching without console
              ProcessBuilder invisibleProcessBuilder = new ProcessBuilder();

              // Set the working directory to where the JAR is located
              LOG.warn("working directory: " + downloadDir);
              invisibleProcessBuilder.directory(downloadDir.toFile());

              // Redirect standard output and error to /dev/null or NUL
              invisibleProcessBuilder.redirectOutput(
                  ProcessBuilder.Redirect.to(
                      new File(osName.contains("win") ? "NUL" : "/dev/null")));
              invisibleProcessBuilder.redirectError(
                  ProcessBuilder.Redirect.to(
                      new File(osName.contains("win") ? "NUL" : "/dev/null")));

              if (osName.contains("win")) {
                // For Windows, use javaw instead of java to avoid console window
                String javawCommand = command.toString().replace("java.exe", "javaw.exe");
                LOG.warn(
                    "Starting MyLocalTon with command: "
                        + "cd \""
                        + downloadDir
                        + "\" && "
                        + javawCommand);
                invisibleProcessBuilder.command(
                    "cmd.exe", "/c", "cd " + downloadDir + " && " + javawCommand);
              } else {
                // For macOS and Linux, use java with appropriate flags
                LOG.warn("Starting MyLocalTon with command: " + command);
                invisibleProcessBuilder.command("sh", "-c", command + " &");
              }

              // Start the process and immediately detach from it
              process = invisibleProcessBuilder.start();
              process.getInputStream().close();
              process.getOutputStream().close();
              process.getErrorStream().close();

              // For extra safety, start a thread to ensure we don't wait for the process
              new Thread(
                      () -> {
                        try {
                          process.waitFor(100, TimeUnit.MILLISECONDS);
                        } catch (Exception ex) {
                          // Ignore any exceptions
                        }
                      })
                  .start();

              showCopiedMessage("Starting...");

            } catch (Exception ex) {
              LOG.warn("Error executing command: " + ex.getMessage(), ex);
              SwingUtilities.invokeLater(
                  () ->
                      Messages.showErrorDialog(
                          project,
                          "Error executing command: " + ex.getMessage(),
                          "MyLocalTon Plugin"));
            }
          }
        });

    stopButton = new JButton("Stop");
    stopButton.addActionListener(
        e -> {
          LOG.warn("Stop button clicked");

          try {
            // Get the operating system
            String osName = System.getProperty("os.name").toLowerCase();
            String jarFilename = getJarFilename(testnetCheckbox.isSelected());

            if (osName.contains("win")) {
              // Windows: Use WMIC command to find all process IDs
              String wmiCommand =
                  "wmic process where \"CommandLine like '%%" + jarFilename + "%%'\" get ProcessId";
              LOG.warn("WMI command: " + wmiCommand);
              ProcessBuilder wmiProcessBuilder = new ProcessBuilder("cmd.exe", "/c", wmiCommand);
              Process wmiProcess = wmiProcessBuilder.start();
              String output =
                  IOUtils.toString(wmiProcess.getInputStream(), Charset.defaultCharset());
              wmiProcess.waitFor();

              // Parse the output to get all process IDs
              String[] lines = output.trim().split("\\s+");
              java.util.List<Long> pids = new ArrayList<>();
              for (String line : lines) {
                if (line.matches("\\d+")) {
                  pids.add(Long.parseLong(line));
                }
              }

              LOG.warn("Found " + pids.size() + " processes to terminate");

              // Get the path to the SendSignalCtrlC64.exe utility
              String p =
                  Paths.get(
                          System.getProperty("user.home"),
                          ".mylocalton/myLocalTon/utils/SendSignalCtrlC64.exe")
                      .toString();

              // Terminate each process
              for (Long pid : pids) {
                LOG.warn("Sending SIGTERM : " + p + " " + pid);
                ProcessBuilder terminateProcessBuilder = new ProcessBuilder(p, pid.toString());
                terminateProcessBuilder.start();
              }
            } else {

              String shell;
              if (osName.contains("mac")) {
                shell = "/bin/zsh";
              } else {
                shell = "/bin/sh";
              }
              String[] command = {
                shell,
                "-c",
                "\"" + getJpsPath() + "\"" + " | grep " + jarFilename + "| awk '{print $1}'"
              };
              LOG.warn("cmd: " + Arrays.toString(command));
              ProcessBuilder processBuilder = new ProcessBuilder(command);
              Process mltProcess = processBuilder.start();
              String pid = IOUtils.toString(mltProcess.getInputStream(), Charset.defaultCharset());
              mltProcess.waitFor();
              ProcessBuilder killProcessBuilder;

              if (osName.contains("mac")) {
                LOG.warn("kill -SIGTERM " + pid.trim());
                killProcessBuilder = new ProcessBuilder(shell, "-c", "kill -SIGTERM " + pid.trim());
                killProcessBuilder.redirectError(
                    ProcessBuilder.Redirect.to(
                        new File(SystemUtils.IS_OS_WINDOWS ? "NUL" : "/dev/null")));
              } else {
                LOG.warn("kill -15 " + pid.trim());
                killProcessBuilder = new ProcessBuilder("sh", "-c", "kill -15 " + pid.trim());
                killProcessBuilder.redirectError(
                    ProcessBuilder.Redirect.to(
                        new File(SystemUtils.IS_OS_WINDOWS ? "NUL" : "/dev/null")));
              }
              Process killerProcess = killProcessBuilder.start();
              killerProcess.waitFor();
            }

            // Set the process running flag to false
            isProcessRunning = false;

            showCopiedMessage("Stopping...");

            resetButton.setEnabled(true);
            deleteButton.setEnabled(true);

            // Re-enable the startup panel when stop button is clicked
            // We'll do this after a short delay to allow the process to stop
            Timer enableTimer =
                new Timer(
                    1000,
                    event -> {
                      // Update the status label which will also update the startup panel state
                      updateStatusLabel();
                    });
            enableTimer.setRepeats(false);
            enableTimer.start();

          } catch (Exception ex) {
            LOG.warn("Error stopping process: " + ex.getMessage(), ex);
            // If an exception occurred, try one more time with destroyForcibly()
            if (process != null) {
              process.destroyForcibly();
              updateStatusLabel();
              resetButton.setEnabled(true);
              deleteButton.setEnabled(true);
            }
          }
        });

    // Center-align buttons
    startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    stopButton.setAlignmentX(Component.CENTER_ALIGNMENT);

    // Set preferred size for buttons
    Dimension buttonSize = new Dimension(150, 30);
    startButton.setPreferredSize(buttonSize);
    stopButton.setPreferredSize(buttonSize);
    startButton.setMaximumSize(buttonSize);
    stopButton.setMaximumSize(buttonSize);

    // Add buttons with spacing
    buttonsPanel.add(Box.createVerticalGlue());
    buttonsPanel.add(startButton);
    buttonsPanel.add(Box.createVerticalStrut(10));
    buttonsPanel.add(stopButton);
    buttonsPanel.add(Box.createVerticalGlue());

    panel.add(buttonsPanel, BorderLayout.CENTER);

    // Create a separate panel for the info label
    JPanel infoPanel = new JPanel();
    infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

    // Add info label in its own panel
    infoLabel = new JLabel(" "); // Space character to maintain height
    infoLabel.setPreferredSize(new Dimension(180, 15));
    infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    infoLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center text within the label
    // infoLabel.setForeground(new Color(255, 165, 0)); // Orange color for better visibility
    // infoLabel.setFont(infoLabel.getFont().deriveFont(Font.BOLD)); // Make text bold

    // Center the info label horizontally
    JPanel infoLabelPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    infoLabelPanel.add(infoLabel);
    infoPanel.add(infoLabelPanel);

    // Add the info panel below the buttons panel
    panel.add(infoPanel, BorderLayout.NORTH); // Place at the top for better visibility

    // Create bottom panel with GridBagLayout for precise positioning
    JPanel southPanel = new JPanel(new GridBagLayout());

    // Create a panel with BoxLayout for vertical arrangement of links
    JPanel linksPanel = new JPanel();
    linksPanel.setLayout(new BoxLayout(linksPanel, BoxLayout.Y_AXIS));

    // Create the links
    String tonlibName = new File(getTonlibPath(System.getProperty("user.home"))).getName();
    JLabel tonlibLink = createLink(tonlibName, project, null);
    JLabel configLink = createLink("global.config.json", project, null);
    JLabel myLocalTonLogLink = createLink("myLocalTon.log", project, null);

    // Add click handler for myLocalTonLogLink to open the log file
    myLocalTonLogLink.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            LOG.warn("myLocalTon.log link clicked");
            String userHome = System.getProperty("user.home");
            String osName = System.getProperty("os.name").toLowerCase();
            String logFilePath;

            // Determine the appropriate path based on OS
            if (osName.contains("win")) {
              logFilePath = userHome + "\\.mylocalton\\myLocalTon\\myLocalTon.log";
            } else {
              logFilePath = userHome + "/.mylocalton/myLocalTon/myLocalTon.log";
            }

            try {
              // Create a File object for the log file
              File logFile = new File(logFilePath);

              // Check if the file exists
              if (!logFile.exists()) {
                SwingUtilities.invokeLater(
                    () ->
                        Messages.showErrorDialog(
                            project, "Log file not found at: " + logFilePath, "MyLocalTon Plugin"));
                return;
              }

              // Open the file with the default text editor
              if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(logFile);
              } else {
                // Fallback for systems where Desktop is not supported
                SwingUtilities.invokeLater(
                    () ->
                        Messages.showInfoMessage(
                            project, "Log file location: " + logFilePath, "MyLocalTon Plugin"));
              }
            } catch (Exception ex) {
              LOG.warn("Error opening log file: " + ex.getMessage(), ex);
              SwingUtilities.invokeLater(
                  () ->
                      Messages.showErrorDialog(
                          project,
                          "Error opening log file: " + ex.getMessage(),
                          "MyLocalTon Plugin"));
            }
          }
        });

    // Add click handler for tonlibLink to copy path to clipboard
    tonlibLink.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            LOG.warn("tonlibjson link clicked");
            String userHome = System.getProperty("user.home");
            String tonlibPath = getTonlibPath(userHome);

            // Copy path to clipboard
            copyToClipboard(tonlibPath);

            // Show info message and set timer to hide it
            showCopiedMessage("Absolute path was copied");
          }
        });

    // Add click handler for configLink to copy path to clipboard
    configLink.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            LOG.warn("global.config.json link clicked");
            String userHome = System.getProperty("user.home");
            String configPath = getGlobalConfigPath(userHome);

            // Copy path to clipboard
            copyToClipboard(configPath);

            // Show info message and set timer to hide it
            showCopiedMessage("Absolute path was copied");
          }
        });

    // Create panels with left alignment for each link and minimal vertical padding
    JPanel firstLinkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    firstLinkPanel.add(tonlibLink);

    JPanel secondLinkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    secondLinkPanel.add(configLink);

    JPanel thirdLinkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    thirdLinkPanel.add(myLocalTonLogLink);

    // Add all panels to the linksPanel vertically with minimal spacing
    linksPanel.add(firstLinkPanel);
    linksPanel.add(Box.createVerticalStrut(1)); // Minimal vertical spacing
    linksPanel.add(secondLinkPanel);
    linksPanel.add(Box.createVerticalStrut(1)); // Minimal vertical spacing
    linksPanel.add(thirdLinkPanel);

    // Create constraints for the links panel (bottom left)
    GridBagConstraints linkConstraints = new GridBagConstraints();
    linkConstraints.gridx = 0;
    linkConstraints.gridy = 0;
    linkConstraints.anchor = GridBagConstraints.SOUTHWEST;
    linkConstraints.weightx = 1.0;
    linkConstraints.weighty = 1.0;
    southPanel.add(linksPanel, linkConstraints);

    // Create status label
    statusLabel = new JLabel("Status: Ready");

    // Create constraints for the status label (bottom right)
    GridBagConstraints statusConstraints = new GridBagConstraints();
    statusConstraints.gridx = 1;
    statusConstraints.gridy = 0;
    statusConstraints.anchor = GridBagConstraints.SOUTHEAST;
    statusConstraints.weightx = 0.0;
    statusConstraints.weighty = 1.0;
    statusConstraints.insets = new Insets(0, 0, 5, 10); // Add some padding at the bottom and right
    southPanel.add(statusLabel, statusConstraints);

    // Initial check of lock file status and update button states
    updateStatusLabel();

    panel.add(southPanel, BorderLayout.SOUTH);

    return panel;
  }

  private @NotNull String getGlobalConfigPath(String userHome) {
    String osName = System.getProperty("os.name").toLowerCase();
    String configPath;

    // Determine the appropriate path based on OS
    if (osName.contains("win")) {
      configPath = userHome + "\\.mylocalton\\myLocalTon\\genesis\\db\\my-ton-local.config.json";
    } else {
      // For Linux and macOS
      configPath = userHome + "/.mylocalton/myLocalTon/genesis/db/my-ton-local.config.json";
    }
    return configPath;
  }

  private @NotNull String getTonlibPath(String userHome) {
    String osName = System.getProperty("os.name").toLowerCase();
    String tonlibPath;

    // Determine the appropriate path based on OS
    if (osName.contains("win")) {
      tonlibPath = userHome + "\\.mylocalton\\myLocalTon\\genesis\\bin\\tonlibjson.dll";
    } else if (osName.contains("mac")) {
      tonlibPath = userHome + "/.mylocalton/myLocalTon/genesis/bin/tonlibjson.dylib";
    } else {
      // Assume Linux or other Unix-like OS
      tonlibPath = userHome + "/.mylocalton/myLocalTon/genesis/bin/tonlibjson.so";
    }
    return tonlibPath;
  }

  private @NotNull String getLiteClientPath(String userHome) {
    String osName = System.getProperty("os.name").toLowerCase();
    String tonlibPath;

    // Determine the appropriate path based on OS
    if (osName.contains("win")) {
      tonlibPath = userHome + "\\.mylocalton\\myLocalTon\\genesis\\bin\\lite-client.exe";
    } else if (osName.contains("mac")) {
      tonlibPath = userHome + "/.mylocalton/myLocalTon/genesis/bin/lite-client";
    } else {
      // Assume Linux or other Unix-like OS
      tonlibPath = userHome + "/.mylocalton/myLocalTon/genesis/bin/lite-client";
    }
    return tonlibPath;
  }

  private @NotNull String getDuPath(String userHome) {
    String osName = System.getProperty("os.name").toLowerCase();
    String tonlibPath = "";

    // Determine the appropriate path based on OS
    if (osName.contains("win")) {
      tonlibPath = userHome + "\\.mylocalton\\myLocalTon\\utils\\du.exe";
    }
    return tonlibPath;
  }

  private static @NotNull String getMyLocalTonPath(String userHome) {
    String osName = System.getProperty("os.name").toLowerCase();
    String configPath;

    // Determine the appropriate path based on OS
    if (osName.contains("win")) {
      configPath = userHome + "\\.mylocalton\\myLocalTon";
    } else {
      // For Linux and macOS
      configPath = userHome + "/.mylocalton/myLocalTon";
    }
    return configPath;
  }

  private JPanel createUninstallPanel(Project project) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Uninstall",
            TitledBorder.LEFT,
            TitledBorder.TOP));

    // Create a panel for buttons with GridBagLayout for better alignment
    JPanel buttonPanel = new JPanel(new GridBagLayout());

    // Create Reset button
    resetButton = createResetButton("Reset", project);
    resetButton.setToolTipText(
        "<html>Deletes only current state of the blockchain<br>and allows to it with different parameters from scratch.</html>");
    resetButton.setPreferredSize(new Dimension(150, 30));

    // Create Delete button
    deleteButton = new JButton("Delete");
    deleteButton.setPreferredSize(new Dimension(150, 30));
    deleteButton.setToolTipText(
        "<html>Completely removes MyLocalTon from your computer.<br>You will have to download it again.</html>");
    deleteButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn("Delete button clicked");

            // Confirm deletion with the user
            int result =
                Messages.showYesNoDialog(
                    project,
                    "Are you sure you want to delete MyLocalTon and all its data?",
                    "Confirm Deletion",
                    Messages.getQuestionIcon());

            if (result == Messages.YES) {
              try {
                // Get the path to the .mylocalton directory
                String userHome = System.getProperty("user.home");
                Path mylocaltonDir = Paths.get(userHome, ".mylocalton");

                if (Files.exists(mylocaltonDir)) {
                  try {
                    // Delete all files and subdirectories inside the directory, but keep the
                    // directory itself
                    FileUtils.cleanDirectory(mylocaltonDir.toFile());
                    Path lockFilePath = Paths.get(userHome, "myLocalTon.lock");
                    Files.deleteIfExists(lockFilePath);

                  } catch (IOException ex) {
                    // If an IOException occurs, it means deletion failed
                    LOG.warn("Failed to delete MyLocalTon content: " + ex.getMessage(), ex);
                    SwingUtilities.invokeLater(
                        () ->
                            Messages.showErrorDialog(
                                project,
                                "Failed to delete MyLocalTon content. Please check if the MyLocalTon process is not running.",
                                "Deletion Failed"));
                  }
                }
                messageLabel.setText("MyLocalTon has been successfully uninstalled");
                Timer hideTimer =
                    new Timer(
                        5000,
                        event -> {
                          messageLabel.setText(" ");
                        });
                hideTimer.setRepeats(false);
                hideTimer.start();

                // Update the download button in the Installation panel
                JPanel mainPanel = (JPanel) panel.getParent();
                if (mainPanel != null) {
                  updateDownloadButtonAfterDeletion(mainPanel);

                  // Disable startup settings panel
                  if (startupSettingsPanel != null) {
                    startupSettingsPanel.setEnabled(false);
                    setEnabledRecursively(startupSettingsPanel, false);
                  }

                  // Disable actions panel (index 2 in the main panel)
                  if (mainPanel.getComponentCount() > 2) {
                    Component actionsPanel = mainPanel.getComponent(2);
                    if (actionsPanel instanceof JPanel) {
                      actionsPanel.setEnabled(false);
                      setEnabledRecursively((Container) actionsPanel, false);
                    }
                  }
                }

              } catch (Exception ex) {
                LOG.warn("Error deleting MyLocalTon content: " + ex.getMessage(), ex);
                SwingUtilities.invokeLater(
                    () ->
                        Messages.showErrorDialog(
                            project,
                            "Error deleting MyLocalTon content: "
                                + ex.getMessage()
                                + "\nPlease check if the MyLocalTon process is not running.",
                            "Deletion Failed"));
              }
            }
          }
        });

    // Create constraints for the buttons
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.insets = new Insets(5, 5, 5, 5);

    // Add Reset button centered
    buttonPanel.add(resetButton, gbc);

    // Move to next position
    gbc.gridx = 1;

    // Add Delete button centered
    buttonPanel.add(deleteButton, gbc);

    panel.add(buttonPanel, BorderLayout.CENTER);

    // Create a message label and add it below the buttons
    messageLabel = new JLabel(" ");
    messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
    messageLabel.setPreferredSize(new Dimension(300, 20));

    // Add the message label to the bottom of the panel
    panel.add(messageLabel, BorderLayout.SOUTH);

    return panel;
  }

  /**
   * Updates the installation panel after deletion.
   *
   * @param mainPanel The main panel containing all sections
   */
  private void updateDownloadButtonAfterDeletion(JPanel mainPanel) {
    // Reset download button state
    downloadButton.setText("DOWNLOAD");
    downloadButton.setEnabled(true);
    testnetCheckbox.setEnabled(true);

    // Reset version label to hide version information
    versionLabel.setText(" ");

    // Clear any "Open Location" links from the bottom panel
    JPanel installationPanel = (JPanel) mainPanel.getComponent(0); // Get the installation panel
    if (installationPanel != null) {
      JPanel bottomPanel = (JPanel) installationPanel.getComponent(2); // Get the bottom panel
      if (bottomPanel != null) {
        bottomPanel.removeAll();

        // Recreate the bottom panel with the same BoxLayout
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

        // Add flexible space to push the checkbox to the right
        bottomPanel.add(Box.createHorizontalGlue());

        // Add Testnet checkbox on the right
        bottomPanel.add(testnetCheckbox);

        // Add padding around the panel
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        bottomPanel.revalidate();
        bottomPanel.repaint();
      }
    }
  }

  /**
   * Recursively sets the enabled state of a container and all its child components.
   *
   * @param container The container to set the enabled state for
   * @param enabled The enabled state to set
   */
  private void setEnabledRecursively(Container container, boolean enabled) {
    container.setEnabled(enabled);

    // Process all components in the container
    for (Component component : container.getComponents()) {
      component.setEnabled(enabled);

      // If the component is a container, process its children recursively
      if (component instanceof Container) {
        setEnabledRecursively((Container) component, enabled);
      }
    }
  }

  private JButton createResetButton(String text, Project project) {
    JButton button = new JButton(text);
    button.addActionListener(
        e -> {
          LOG.warn("reset button clicked");
          String userHome = System.getProperty("user.home");
          Path mylocaltonDir = Paths.get(userHome, ".mylocalton/myLocalTon");

          try {
            if (Files.exists(mylocaltonDir)) {
              FileUtils.cleanDirectory(mylocaltonDir.toFile());
              Path lockFilePath = Paths.get(userHome, "myLocalTon.lock");
              Files.deleteIfExists(lockFilePath);
              messageLabel.setText("Blockchain state has been cleared");
              Timer hideTimer =
                  new Timer(
                      5000,
                      event -> {
                        messageLabel.setText(" ");
                      });
              hideTimer.setRepeats(false);
              hideTimer.start();
            } else {
              messageLabel.setText("No blockchain state found");
              Timer hideTimer =
                  new Timer(
                      5000,
                      event -> {
                        messageLabel.setText(" ");
                      });
              hideTimer.setRepeats(false);
              hideTimer.start();
            }
          } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
            messageLabel.setText("Reset failed.");
          }
        });
    return button;
  }

  private JLabel createLink(String text, Project project, String message) {
    JLabel link = new JLabel("<html><a href=\"#\">" + text + "</a></html>");
    link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // Only add the default click handler if a message is provided
    if (message != null) {
      link.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              LOG.warn(text + " link clicked");
              SwingUtilities.invokeLater(
                  () -> Messages.showInfoMessage(project, message, "MyLocalTon Plugin"));
            }
          });
    }

    return link;
  }

  /**
   * Downloads a file from the specified URL and updates the progress bar.
   *
   * @param fileUrl URL of the file to download
   * @param targetFile File where the downloaded content will be saved
   * @param progressBar JProgressBar to update with download progress
   * @throws IOException If an I/O error occurs during download
   */
  /**
   * Copies the given text to the system clipboard.
   *
   * @param text The text to copy to the clipboard
   */
  private void copyToClipboard(String text) {
    StringSelection selection = new StringSelection(text);
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    LOG.warn("Copied to clipboard: " + text);
  }

  /**
   * Shows the "absolute path was copied" message in the info label and sets a timer to hide it
   * after 3 seconds.
   */
  private void showCopiedMessage(String text) {
    // Update the info label
    infoLabel.setText(text);

    // Create a timer to reset the message after 3 seconds
    Timer hideTimer =
        new Timer(
            3000,
            new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                infoLabel.setText(" "); // Reset to space character to maintain layout
                // Ensure horizontal alignment is maintained
                infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
              }
            });
    hideTimer.setRepeats(false); // Only execute once
    hideTimer.start();
  }

  private void downloadFile(String fileUrl, File targetFile, JProgressBar progressBar)
      throws IOException, URISyntaxException {
    URI uri = new URI(fileUrl);
    URL url = uri.toURL();
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    // Set up the connection
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(10000); // 10 seconds
    connection.setReadTimeout(60000); // 60 seconds

    // Get file size if available
    int fileSize = connection.getContentLength();

    // Open streams
    try (InputStream in = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(targetFile)) {

      byte[] buffer = new byte[8192]; // 8KB buffer
      int bytesRead;
      long totalBytesRead = 0;

      // Read from input and write to output
      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
        totalBytesRead += bytesRead;

        // Update progress if file size is known
        if (fileSize > 0) {
          final int progress = (int) (totalBytesRead * 100 / fileSize);
          SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
        }
      }

      // Ensure progress bar shows 100% when done
      SwingUtilities.invokeLater(() -> progressBar.setValue(100));
    }
  }

  public String getDirectorySizeUsingDu(String path) {
    String resultInput = "0MB";
    if (SystemUtils.IS_OS_WINDOWS) {
      try {

        String cmd = getDuPath(System.getProperty("user.home")) + " -hs " + path;
        LOG.debug(cmd);
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", cmd);
        Process p = processBuilder.start();
        InputStream procOutput = p.getInputStream();

        resultInput = IOUtils.toString(procOutput, Charset.defaultCharset());
        LOG.debug(resultInput);
        String[] s = resultInput.split("\t");

        return s[0];
      } catch (Exception e) {
        LOG.error("cannot get folder size on windows {}", path);
        return resultInput;
      }
    } else {
      try {
        String cmd = "du -hs " + path;
        LOG.debug(cmd);
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", cmd);
        Process p = processBuilder.start();
        InputStream procOutput = p.getInputStream();

        resultInput = IOUtils.toString(procOutput, Charset.defaultCharset());
        LOG.debug(resultInput);
        String[] s = resultInput.split("\t");

        return s[0];
      } catch (Exception e) {
        LOG.error("cannot get folder size on linux {}", path);
        return resultInput;
      }
    }
  }

  public static String getBundledJrePath(String executable) {
    try {
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        SdkTypeId sdkType = sdk.getSdkType();
        if (sdkType instanceof SdkType) {
          return sdk.getHomePath()
              + "/bin/"
              + (SystemInfo.isWindows ? executable + ".exe" : executable);
        }
      }
      return System.getProperty("java.home")
          + File.separator
          + "bin"
          + File.separator
          + (SystemInfo.isWindows ? executable + ".exe" : executable);

    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return executable;
    }
  }

  public static String getExecutableFromSystem(String executable) {
    try {
      String sdkHome = System.getProperty("java.home");

      if (sdkHome == null) {
        LOG.error("Cannot get system sdkHome");
      }

      return sdkHome
          + File.separator
          + "bin"
          + File.separator
          + (SystemInfo.isWindows ? executable + ".exe" : executable);
    } catch (Exception e) {
      LOG.error("Cannot get sdkHome, " + e.getMessage());
      return executable;
    }
  }

  public static String getJavaPath() {
    String path = getBundledJrePath("java");
    String versionCommand = "\"" + path + "\"" + " --version";
    String version = executeProcess(versionCommand);
    if (StringUtils.isNotEmpty(version)) {
      return path;
    } else {
      path = getExecutableFromSystem("java");
      version = executeProcess(versionCommand);
      if (StringUtils.isNotEmpty(version)) {
        return path;
      } else {
        LOG.error("cannot detect java path");
        return "java";
      }
    }
  }

  public static String getJavaVersion() {
    String versionCommand = "\"" + getJavaPath() + "\"" + " --version";
    return executeProcess(versionCommand);
  }

  public static String getJpsPath() {
    String path = getBundledJrePath("jps");
    if (StringUtils.isNotEmpty(path)) {
      return path;
    } else {
      path = getExecutableFromSystem("jps");
      if (StringUtils.isNotEmpty(path)) {
        return path;
      } else {
        LOG.error("cannot detect jps path");
        return "jps";
      }
    }
  }

  public static String getMyLocalTonVersion(String myLocalTonJarPath) {
    String versionCommand = "\"" + getJavaPath() + "\" -jar \"" + myLocalTonJarPath + "\" version";

    return executeProcess(versionCommand);
  }

  public static int extractJavaMajorVersion(String javaVersionOutput) {
    if (javaVersionOutput == null || javaVersionOutput.isEmpty()) {
      return 0;
    }

    // Match any line with a version number like 21.0.6, 17.0.2, etc.
    Pattern pattern = Pattern.compile("(\\d+)\\.\\d+\\.\\d+");
    Matcher matcher = pattern.matcher(javaVersionOutput);

    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        // Handle exception if needed
      }
    }

    return 0;
  }
}
