package org.ton.mylocalton.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.ton.java.liteclient.LiteClient;
import org.ton.java.liteclient.LiteClientParser;
import org.ton.java.liteclient.api.ResultLastBlock;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/** Factory for creating the Demo Tool Window. */
public class MyLocalTonToolWindowFactory implements ToolWindowFactory {
  // Use static logger to avoid initialization issues
  private static final Logger LOG = Logger.getInstance(MyLocalTonToolWindowFactory.class);
  private JLabel statusLabel;
  private JLabel infoLabel; // Label to show "absolute path was copied" message
  private Timer lockFileMonitor;
  private JButton startButton;
  private JButton stopButton;
  private JButton resetButton;
  private JButton deleteButton;
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
  static {
    LOG.warn("DemoToolWindowFactory class loaded");
  }

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

  /** Updates the status label, button states, and panel states based on the lock file existence. */
  private void updateStatusLabel() {
    boolean lockExists = isLockFileExists();

    if (statusLabel != null) {
      if (lockExists) {
        statusLabel.setText("Status: running");
      } else {
        statusLabel.setText("Status: not running");
      }
    }

    // Update button states based on lock file existence
    if (startButton != null) {
      startButton.setEnabled(!lockExists); // Disable Start when lock exists
    }

    if (stopButton != null) {
      stopButton.setEnabled(lockExists); // Disable Stop when lock doesn't exist
    }

    // Disable/enable the startup settings panel based on lock file existence
    if (startupSettingsPanel != null) {
      startupSettingsPanel.setEnabled(!lockExists); // Disable when lock exists

      // Recursively disable/enable all components inside the panel
      setEnabledRecursively(startupSettingsPanel, !lockExists);
    }
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
      installationPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150)); // Fixed height
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
      Content content =
          contentFactory.createContent(mainPanel, "MyLocalTon", false);
      toolWindow.getContentManager().addContent(content);

      monitorExecutorService = Executors.newSingleThreadScheduledExecutor();
      monitorExecutorService.scheduleWithFixedDelay(
          () -> {
            Thread.currentThread().setName("MyLocalTon-Plugin - Blockchain Monitor");

            try {
              String userHomeDir = System.getProperty("user.home");
              if (isNull(liteClient)) {
                LOG.warn("init liteclient");
                liteClient =
                    LiteClient.builder()
                        .pathToGlobalConfig(getGlobalConfigPath(userHomeDir))
                        .pathToLiteClientBinary(getLiteClientPath(userHomeDir))
                        .build();
              } else {
                LOG.warn("inited liteclient");
              }

              //            String size =  getDirectorySizeUsingDu(getMyLocalTonPath(userHomeDir));
              String last = liteClient.executeLast();
              if (last.contains("latest masterchain block known to server")) {
                ResultLastBlock resultLastBlock = LiteClientParser.parseLast(last);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                //                          java.util.List<ResultLastBlock> shards =
                // LiteClientParser.parseAllShards(liteClient.executeAllshards(resultLastBlock));
                //                        LOG.warn("size last shards "+ size+" "+
                // resultLastBlock.getSeqno()+" "+shards.size());
                statusLabel.setText("Block: " + resultLastBlock.getSeqno());
              }
              else {
                updateStatusLabel();
              }
            } catch (Exception ex) {
              updateStatusLabel();
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
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Installation",
            TitledBorder.LEFT,
            TitledBorder.TOP));

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

    // Create top panel with centered Download button and progress bar
    JPanel topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

    // Download button panel (centered)
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

    // Progress bar panel (centered)
    JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

    // Create progress bar (initially invisible)
    JProgressBar progressBar = new JProgressBar(0, 100);
    progressBar.setValue(0);
    progressBar.setStringPainted(true);
    progressBar.setPreferredSize(new Dimension(150, 20));
    progressBar.setVisible(false); // Initially invisible
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

            // Get testnet checkbox state
            boolean isTestnet = testnetCheckbox.isSelected();

            // Make progress bar visible when Download button is clicked
            progressBar.setVisible(true);

            // Disable the download button during download
            downloadButton.setEnabled(false);
            testnetCheckbox.setEnabled(false);

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
                              // Change download button text and keep it disabled
                              downloadButton.setText("DOWNLOADED");
                              downloadButton.setEnabled(false);
                              testnetCheckbox.setEnabled(false);

                              // Get the bottom panel to add the "Open Location" link
                              JPanel bottomPanel =
                                  (JPanel) panel.getComponent(1); // Get the bottom panel

                              // Clear the bottom panel and recreate it
                              bottomPanel.removeAll();

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
                                          // Fallback for systems where Desktop is not supported
                                          Messages.showInfoMessage(
                                              project,
                                              "Download location: " + dirPath,
                                              "MyLocalTon Plugin");
                                        }
                                      } catch (Exception ex) {
                                        LOG.warn(
                                            "Error opening download location: " + ex.getMessage(),
                                            ex);
                                        Messages.showErrorDialog(
                                            project,
                                            "Error opening download location: " + ex.getMessage(),
                                            "MyLocalTon Plugin");
                                      }
                                    }
                                  });

                              // Recreate the bottom panel with the same BoxLayout
                              bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
                              bottomPanel.add(openLocationLink);

                              // Add flexible space to push the checkbox to the right
                              bottomPanel.add(Box.createHorizontalGlue());

                              // Add Testnet checkbox on the right
                              bottomPanel.add(testnetCheckbox);

                              // Add padding around the panel
                              bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

                              bottomPanel.revalidate();
                              bottomPanel.repaint();

                              Messages.showInfoMessage(
                                  project,
                                  "Download completed successfully!\nFile saved to: " + targetPath,
                                  "MyLocalTon Plugin");
                            });
                      } catch (Exception ex) {
                        LOG.warn("Error downloading file: " + ex.getMessage(), ex);
                        SwingUtilities.invokeLater(
                            () -> {
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

                              Messages.showErrorDialog(
                                  project,
                                  "Error downloading file: " + ex.getMessage(),
                                  "MyLocalTon Plugin");
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

    // Add components to top panel
    topPanel.add(buttonPanel);
    topPanel.add(Box.createVerticalStrut(10)); // Add spacing
    topPanel.add(progressPanel);

    panel.add(topPanel, BorderLayout.NORTH);

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
                  Messages.showInfoMessage(
                      project, "Download location: " + dirPath, "MyLocalTon Plugin");
                }
              } catch (Exception ex) {
                LOG.warn("Error opening download location: " + ex.getMessage(), ex);
                Messages.showErrorDialog(
                    project,
                    "Error opening download location: " + ex.getMessage(),
                    "MyLocalTon Plugin");
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
        boolean enableBlockchainExplorer = jsonContent.contains("\"enableBlockchainExplorer\": true");
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
    tonHttpApiV2.setToolTipText("Enables ton-http-api service on start.");
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
                Messages.showErrorDialog(
                    project,
                    "MyLocalTon JAR file not found. Please download it first.",
                    "MyLocalTon Plugin");
                return;
              }

              // Build the command with parameters based on checkbox states
              StringBuilder command = new StringBuilder();
              command.append("java -jar \"").append(jarPath).append("\"");
              
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
                String javawCommand = command.toString().replace("java ", "javaw ");
                LOG.warn("Starting MyLocalTon with command: " + javawCommand);
                invisibleProcessBuilder.command("cmd.exe", "/c", javawCommand);
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
              Messages.showErrorDialog(
                  project, "Error executing command: " + ex.getMessage(), "MyLocalTon Plugin");
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

              if (osName.contains("win")) {
                // Windows: Use WMIC command to find all process IDs
                String jarFilename = getJarFilename(testnetCheckbox.isSelected());
                String wmiCommand =
                    "wmic process where \"CommandLine like '%%"
                        + jarFilename
                        + "%%'\" get ProcessId";
                LOG.warn("WMI command: " + wmiCommand);
                Process wmiProcess = Runtime.getRuntime().exec(wmiCommand);
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
                  Runtime.getRuntime().exec(p + " " + pid);
                }
              } else {
                // Non-Windows: Use process.pid() to get the process ID
                long pid = process.pid(); // find pid on linux and mac todo
                LOG.warn("Terminating process with PID: " + pid);

                // For Unix-based systems, use kill -15 (SIGTERM)
                Runtime.getRuntime().exec("kill -15 " + pid);
              }

              // Wait for the process to terminate
//              boolean terminated = process.waitFor(5, TimeUnit.SECONDS);

//              if (!terminated) {
//                LOG.warn("Process did not terminate after SIGTERM, forcing destruction");
//                // If the process didn't terminate, use destroyForcibly()
//                process.destroyForcibly();
//              } else {
//                LOG.warn("Process terminated gracefully after SIGTERM");
//              }

//              process = null;
//              updateStatusLabel();
              showCopiedMessage("Stopping...");
            } catch (Exception ex) {
              LOG.warn("Error stopping process: " + ex.getMessage(), ex);
              // If an exception occurred, try one more time with destroyForcibly()
              if (process != null) {
                process.destroyForcibly();
                updateStatusLabel();
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
                Messages.showErrorDialog(
                    project, "Log file not found at: " + logFilePath, "MyLocalTon Plugin");
                return;
              }

              // Open the file with the default text editor
              if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(logFile);
              } else {
                // Fallback for systems where Desktop is not supported
                Messages.showInfoMessage(
                    project, "Log file location: " + logFilePath, "MyLocalTon Plugin");
              }
            } catch (Exception ex) {
              LOG.warn("Error opening log file: " + ex.getMessage(), ex);
              Messages.showErrorDialog(
                  project, "Error opening log file: " + ex.getMessage(), "MyLocalTon Plugin");
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

    // Create a panel for buttons with FlowLayout to place them side by side
    JPanel buttonPanel = new JPanel();
    
    // Create Reset button
    resetButton = createResetButton("Reset", project);
    resetButton.setToolTipText("<html>Deletes only current state of the blockchain<br>and allows to start new blockchain from scratch.</html>");
    resetButton.setPreferredSize(new Dimension(150, 30));
    
    // Create Delete button
    deleteButton = new JButton("Delete");
    deleteButton.setPreferredSize(new Dimension(150, 30));
    deleteButton.setToolTipText("<html>Completely removes MyLocalTon from your computer.<br>You will have to download it again.</html>");
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
                Path mylocaltonDir = Paths.get(System.getProperty("user.home"), ".mylocalton");

                if (Files.exists(mylocaltonDir)) {
                  try {
                    // Delete all files and subdirectories inside the directory, but keep the
                    // directory itself
                    FileUtils.cleanDirectory(mylocaltonDir.toFile());

                    // If we get here, deletion was successful
                    Messages.showInfoMessage(
                        project,
                        "MyLocalTon content has been successfully deleted from your computer.",
                        "MyLocalTon Plugin");

                    // Update the download button in the Installation panel
                    JPanel mainPanel = (JPanel) panel.getParent();
                    if (mainPanel != null) {
                      updateDownloadButtonAfterDeletion(mainPanel);
                    }
                  } catch (IOException ex) {
                    // If an IOException occurs, it means deletion failed
                    LOG.warn("Failed to delete MyLocalTon content: " + ex.getMessage(), ex);
                    Messages.showErrorDialog(
                        project,
                        "Failed to delete MyLocalTon content. Please check if the MyLocalTon process is not running.",
                        "Deletion Failed");
                  }
                } else {
                  Messages.showInfoMessage(
                      project, "MyLocalTon directory not found.", "MyLocalTon Plugin");
                }
              } catch (Exception ex) {
                LOG.warn("Error deleting MyLocalTon content: " + ex.getMessage(), ex);
                Messages.showErrorDialog(
                    project,
                    "Error deleting MyLocalTon content: "
                        + ex.getMessage()
                        + "\nPlease check if the MyLocalTon process is not running.",
                    "Deletion Failed");
              }
            }
          }
        });

    // Add Reset button to the left
    buttonPanel.add(resetButton);
    
    // Add some space between buttons
    buttonPanel.add(Box.createHorizontalStrut(10));
    
    // Add Delete button to the right
    buttonPanel.add(deleteButton);
    
    panel.add(buttonPanel, BorderLayout.CENTER);

    return panel;
  }

  /**
   * Updates the download button in the Installation panel after deletion.
   *
   * @param mainPanel The main panel containing all sections
   */
  private void updateDownloadButtonAfterDeletion(JPanel mainPanel) {

    downloadButton.setText("DOWNLOAD");
    downloadButton.setEnabled(true);
    testnetCheckbox.setEnabled(true);
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
              Path mylocaltonDir = Paths.get(System.getProperty("user.home"), ".mylocalton/myLocalTon");

                try {
                    FileUtils.cleanDirectory(mylocaltonDir.toFile());

                } catch (IOException ex) {
                    throw new RuntimeException(ex);
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
              Messages.showInfoMessage(
                  project, message, "MyLocalTon Plugin");
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
      throws IOException {
    URL url = new URL(fileUrl);
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

        String cmd = getDuPath(System.getProperty("user.home"))+" -hs "+path;
        LOG.debug(cmd);
        Process p = Runtime.getRuntime().exec(cmd);
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
        Process p = Runtime.getRuntime().exec(cmd);
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
}
