package org.ton.mylocalton.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.*;
import javax.swing.border.TitledBorder;

/** Factory for creating the Demo Tool Window. */
public class MyLocalTonToolWindowFactory implements ToolWindowFactory {
  // Use static logger to avoid initialization issues
  private static final Logger LOG = Logger.getInstance(MyLocalTonToolWindowFactory.class);
  private JLabel statusLabel;
  private Timer lockFileMonitor;
  private JButton startButton;
  private JButton stopButton;
  private JButton resetButton;
  private JPanel startupSettingsPanel;

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
  
  /**
   * Updates the status label, button states, and panel states based on the lock file existence.
   */
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
    
    if (resetButton != null) {
      resetButton.setEnabled(lockExists); // Disable Reset when lock doesn't exist
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
      actionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180)); // Fixed height
      mainPanel.add(actionsPanel);
      mainPanel.add(Box.createVerticalStrut(5)); // Reduced spacing for compactness

      // 4. Uninstall Section
      JPanel uninstallPanel = createUninstallPanel(project);
      uninstallPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // Top align
      uninstallPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120)); // Fixed height
      mainPanel.add(uninstallPanel);

      // Add the panel to the tool window
      ContentFactory contentFactory = ContentFactory.getInstance();
      com.intellij.ui.content.Content content =
          contentFactory.createContent(mainPanel, "MyLocalTon", false);
      toolWindow.getContentManager().addContent(content);

      // Start the timer to check the lock file status every 5 seconds
      lockFileMonitor = new Timer(5000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateStatusLabel();
        }
      });
      lockFileMonitor.setInitialDelay(0);
      lockFileMonitor.start();

      LOG.warn("Tool window content created successfully");
    } catch (Exception e) {
      LOG.warn("Error creating tool window content: " + e.getMessage(), e);
    }
  }

  private JPanel createInstallationPanel(Project project) {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Installation",
            TitledBorder.LEFT,
            TitledBorder.TOP));
            
    // Check if JAR file exists
    Path downloadDir = Paths.get(System.getProperty("user.home"), ".mylocalton");
    Path jarPath = downloadDir.resolve("MyLocalTon-x86-64.jar");
    boolean jarExists = Files.exists(jarPath);

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
    JButton downloadButton = new JButton(jarExists ? "DOWNLOADED" : "DOWNLOAD");
    downloadButton.setEnabled(!jarExists); // Disable if JAR exists
    downloadButton.setPreferredSize(new Dimension(150, 30));
    downloadButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn("Download button clicked");

            // Make progress bar visible when Download button is clicked
            progressBar.setVisible(true);

            // Disable the download button during download
            downloadButton.setEnabled(false);

            // Create a thread for downloading the file
            new Thread(
                    () -> {
                      try {
                        // URL of the file to download
                        String fileUrl =
                            "https://github.com/neodix42/mylocalton/releases/latest/download/MyLocalTon-x86-64.jar";

                        // Create a directory for the download if it doesn't exist
                        Path downloadDir =
                            Paths.get(System.getProperty("user.home"), ".mylocalton");
                        if (!Files.exists(downloadDir)) {
                          Files.createDirectories(downloadDir);
                        }

                        // Path where the file will be saved
                        Path targetPath = downloadDir.resolve("MyLocalTon-x86-64.jar");
                        File targetFile = targetPath.toFile();

                        // Download the file and update progress
                        downloadFile(fileUrl, targetFile, progressBar);

                        // Show success message
                        SwingUtilities.invokeLater(
                            () -> {
                              // Change download button text and keep it disabled
                              downloadButton.setText("DOWNLOADED");
                              downloadButton.setEnabled(false);

                              // Get the left panel to add the "Open Location" link
                              JPanel bottomPanel = (JPanel) panel.getComponent(1); // Get the bottom panel
                              JPanel leftPanel = (JPanel) ((BorderLayout) bottomPanel.getLayout()).getLayoutComponent(BorderLayout.WEST);
                              
                              // Clear the left panel and add the "Open Location" link
                              leftPanel.removeAll();
                              
                              // Add "Open Location" link
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
                                          com.intellij.openapi.ui.Messages.showInfoMessage(
                                              project,
                                              "Download location: " + dirPath,
                                              "MyLocalTon Plugin");
                                        }
                                      } catch (Exception ex) {
                                        LOG.warn(
                                            "Error opening download location: " + ex.getMessage(),
                                            ex);
                                        com.intellij.openapi.ui.Messages.showErrorDialog(
                                            project,
                                            "Error opening download location: " + ex.getMessage(),
                                            "MyLocalTon Plugin");
                                      }
                                    }
                                  });
                              leftPanel.add(openLocationLink);
                              leftPanel.revalidate();
                              leftPanel.repaint();

                              com.intellij.openapi.ui.Messages.showInfoMessage(
                                  project,
                                  "Download completed successfully!\nFile saved to: " + targetPath,
                                  "MyLocalTon Plugin");
                            });
                      } catch (Exception ex) {
                        LOG.warn("Error downloading file: " + ex.getMessage(), ex);
                        SwingUtilities.invokeLater(
                            () -> {
                              progressBar.setVisible(false);
                              
                              // Get the left panel to add the "Download failed" label
                              JPanel bottomPanel = (JPanel) panel.getComponent(1); // Get the bottom panel
                              JPanel leftPanel = (JPanel) ((BorderLayout) bottomPanel.getLayout()).getLayoutComponent(BorderLayout.WEST);
                              
                              // Clear the left panel and add the "Download failed" label
                              leftPanel.removeAll();
                              JLabel failedLabel = new JLabel("Download failed.");
                              leftPanel.add(failedLabel);
                              leftPanel.revalidate();
                              leftPanel.repaint();
                              
                              com.intellij.openapi.ui.Messages.showErrorDialog(
                                  project,
                                  "Error downloading file: " + ex.getMessage(),
                                  "MyLocalTon Plugin");
                              // Keep the download button disabled but change text back to original
                              downloadButton.setText("DOWNLOAD");
                              downloadButton.setEnabled(true);
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

    // Create bottom panel with FlowLayout
    JPanel bottomPanel = new JPanel(new BorderLayout());

    // Add download status label to the bottom left
    JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    
    // Set appropriate label based on JAR existence
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
                  com.intellij.openapi.ui.Messages.showInfoMessage(
                      project,
                      "Download location: " + dirPath,
                      "MyLocalTon Plugin");
                }
              } catch (Exception ex) {
                LOG.warn(
                    "Error opening download location: " + ex.getMessage(),
                    ex);
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Error opening download location: " + ex.getMessage(),
                    "MyLocalTon Plugin");
              }
            }
          });
      leftPanel.add(openLocationLink);
    } else {
      // JAR doesn't exist, but we don't need to show any label
    }
    bottomPanel.add(leftPanel, BorderLayout.WEST);

    // Add Testnet checkbox to the bottom right
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JCheckBox testnetCheckbox = new JCheckBox("Testnet");
    testnetCheckbox.setToolTipText("Download MyLocalTon based on TON binaries from testnet branch.");
    rightPanel.add(testnetCheckbox);
    bottomPanel.add(rightPanel, BorderLayout.EAST);

    panel.add(bottomPanel, BorderLayout.SOUTH);

    return panel;
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

    JCheckBox checkbox1 = new JCheckBox("TON HTTP API v2");
    checkbox1.setToolTipText("Enables ton-http-api service on start.");
    JCheckBox checkbox2 = new JCheckBox("Web explorer");
    checkbox2.setToolTipText("Enables native TON blockchain web explorer on start.");
    JCheckBox checkbox3 = new JCheckBox("Data generator");
    checkbox3.setToolTipText("Enables dummy data-generator on start.");
    JCheckBox checkbox4 = new JCheckBox("No GUI mode");
    checkbox4.setToolTipText("Launches MyLocalTon without GUI.");
    
    // Create a panel for the listbox and label to be placed below "No GUI mode"
    JPanel listboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    
    // Create a listbox (JComboBox) with numbers 0 to 5
    Integer[] numbers = {0, 1, 2, 3, 4, 5};
    JComboBox<Integer> listbox = new JComboBox<>(numbers);
    
    // Make the combobox width two times smaller
    Dimension comboBoxSize = listbox.getPreferredSize();
    comboBoxSize.width = comboBoxSize.width - 20;
    listbox.setPreferredSize(comboBoxSize);

    // Add the "add validators" label next to the listbox
    JLabel validatorsLabel = new JLabel("Validators:");
    listboxPanel.add(validatorsLabel);

    // Add the listbox to the panel
    listboxPanel.add(listbox);
    
    JCheckBox checkbox5 = new JCheckBox("Debug mode");
    checkbox5.setToolTipText("Launches MyLocalTon in debug mode that add lots of useful information into log files.");
    
    checkboxPanel.add(checkbox1);
    checkboxPanel.add(checkbox2);
    checkboxPanel.add(checkbox3);
    checkboxPanel.add(checkbox4);
    checkboxPanel.add(listboxPanel);
    checkboxPanel.add(checkbox5);

    contentPanel.add(checkboxPanel);
    panel.add(contentPanel, BorderLayout.CENTER);

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

    // Create center panel with buttons
    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

    // Create buttons
    startButton = new JButton("Start");
    startButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn("Start button clicked");
            
            try {
              // Get the path to the downloaded JAR file
              Path downloadDir = Paths.get(System.getProperty("user.home"), ".mylocalton");
              Path jarPath = downloadDir.resolve("MyLocalTon-x86-64.jar");
              
              if (!Files.exists(jarPath)) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project, 
                    "MyLocalTon-x86-64.jar not found. Please download it first.", 
                    "MyLocalTon Plugin");
                return;
              }
              
              // Build the command with parameters based on checkbox states
              StringBuilder command = new StringBuilder();
              command.append("java -jar \"").append(jarPath.toString()).append("\"");
              
              // Execute the command
              ProcessBuilder processBuilder = new ProcessBuilder();
              
              // Set the working directory to where the JAR is located
              processBuilder.directory(downloadDir.toFile());
              
              // Set the command based on the OS
              if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", command.toString());
              } else {
                processBuilder.command("sh", "-c", command.toString());
              }
              
              // Redirect error stream to output stream
              processBuilder.redirectErrorStream(true);
              
              // Start the process
              Process process = processBuilder.start();
              
              // Show a message with the executed command
              com.intellij.openapi.ui.Messages.showInfoMessage(
                  project, 
                  "Executing command: " + command.toString(), 
                  "MyLocalTon Plugin");
              
            } catch (Exception ex) {
              LOG.warn("Error executing command: " + ex.getMessage(), ex);
              com.intellij.openapi.ui.Messages.showErrorDialog(
                  project,
                  "Error executing command: " + ex.getMessage(),
                  "MyLocalTon Plugin");
            }
          }
        });
    
    stopButton = createButton("Stop", project, "Stop operation initiated!");
    resetButton = createButton("Reset", project, "Reset operation initiated!");
    resetButton.setToolTipText("Reset MyLocalTon to its default state");

    // Center-align buttons
    startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    stopButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);

    // Set preferred size for buttons
    Dimension buttonSize = new Dimension(150, 30);
    startButton.setPreferredSize(buttonSize);
    stopButton.setPreferredSize(buttonSize);
    resetButton.setPreferredSize(buttonSize);
    startButton.setMaximumSize(buttonSize);
    stopButton.setMaximumSize(buttonSize);
    resetButton.setMaximumSize(buttonSize);

    // Add buttons with spacing
    centerPanel.add(Box.createVerticalGlue());
    centerPanel.add(startButton);
    centerPanel.add(Box.createVerticalStrut(10));
    centerPanel.add(stopButton);
    centerPanel.add(Box.createVerticalStrut(10));
    centerPanel.add(resetButton);
    centerPanel.add(Box.createVerticalGlue());

    panel.add(centerPanel, BorderLayout.CENTER);

    // Create bottom panel with BorderLayout
    JPanel southPanel = new JPanel(new BorderLayout());

    // Add links to the bottom left
    JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    // Create tonlib.dll link
    JLabel tonlibLink = createLink("tonlib.dll", project, "tonlib.dll clicked");
    leftPanel.add(tonlibLink);

    // Add spacing between links
    leftPanel.add(Box.createHorizontalStrut(10));

    // Create global.config.json link
    JLabel configLink = createLink("global.config.json", project, "global.config.json clicked");
    leftPanel.add(configLink);

    southPanel.add(leftPanel, BorderLayout.WEST);

    // Add status label to the bottom right
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    statusLabel = new JLabel("Status: Ready");
    rightPanel.add(statusLabel);
    southPanel.add(rightPanel, BorderLayout.EAST);
    
    // Initial check of lock file status and update button states
    updateStatusLabel();

    panel.add(southPanel, BorderLayout.SOUTH);

    return panel;
  }

  private JPanel createUninstallPanel(Project project) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            "Uninstall",
            TitledBorder.LEFT,
            TitledBorder.TOP));

    // Delete button
    JButton deleteButton = new JButton("Delete");
    deleteButton.setPreferredSize(new Dimension(150, 30));
    deleteButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn("Delete button clicked");
            
            // Confirm deletion with the user
            int result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                project,
                "Are you sure you want to delete MyLocalTon and all its data?",
                "Confirm Deletion",
                com.intellij.openapi.ui.Messages.getQuestionIcon());
                
            if (result == com.intellij.openapi.ui.Messages.YES) {
              try {
                // Get the path to the .mylocalton directory
                Path mylocaltonDir = Paths.get(System.getProperty("user.home"), ".mylocalton");
                
                if (Files.exists(mylocaltonDir)) {
                  try {
                    // Delete all files and subdirectories inside the directory, but keep the directory itself
                    FileUtils.cleanDirectory(mylocaltonDir.toFile());
                    
                    // If we get here, deletion was successful
                    com.intellij.openapi.ui.Messages.showInfoMessage(
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
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Failed to delete MyLocalTon content. Please check if the MyLocalTon process is not running.",
                        "Deletion Failed");
                  }
                } else {
                  com.intellij.openapi.ui.Messages.showInfoMessage(
                      project, 
                      "MyLocalTon directory not found.",
                      "MyLocalTon Plugin");
                }
              } catch (Exception ex) {
                LOG.warn("Error deleting MyLocalTon content: " + ex.getMessage(), ex);
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Error deleting MyLocalTon content: " + ex.getMessage() + 
                    "\nPlease check if the MyLocalTon process is not running.",
                    "Deletion Failed");
              }
            }
          }
        });

    // Center the button
    JPanel buttonPanel = new JPanel();
    buttonPanel.add(deleteButton);
    panel.add(buttonPanel, BorderLayout.CENTER);

    // Add label below Delete button, aligned to the right
    JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    // Create a multiline label with HTML to enable text wrapping
    JLabel uninstallStatusLabel = new JLabel("<html><div style='width:250px; align:left;'>Completely removes MyLocalTon from your computer. You will have to download it again.</div></html>");
    southPanel.add(uninstallStatusLabel);
    panel.add(southPanel, BorderLayout.SOUTH);

    return panel;
  }
  
  /**
   * Updates the download button in the Installation panel after deletion.
   *
   * @param mainPanel The main panel containing all sections
   */
  private void updateDownloadButtonAfterDeletion(JPanel mainPanel) {
    try {
      // Find the installation panel
      for (Component comp : mainPanel.getComponents()) {
        if (comp instanceof JPanel) {
          JPanel panelComp = (JPanel) comp;
          if (panelComp.getBorder() instanceof TitledBorder) {
            TitledBorder border = (TitledBorder) panelComp.getBorder();
            if ("Installation".equals(border.getTitle())) {
              // Found the installation panel
              JPanel installationPanel = panelComp;
              
              // Find the top panel with the download button
              Component northComp = ((BorderLayout) installationPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
              if (northComp instanceof JPanel) {
                JPanel topPanel = (JPanel) northComp;
                
                // Find the button panel
                if (topPanel.getComponentCount() > 0 && topPanel.getComponent(0) instanceof JPanel) {
                  JPanel buttonPanel = (JPanel) topPanel.getComponent(0);
                  
                  // Find the download button
                  for (Component buttonComp : buttonPanel.getComponents()) {
                    if (buttonComp instanceof JButton) {
                      JButton button = (JButton) buttonComp;
                      if ("DOWNLOADED".equals(button.getText())) {
                        // Reset the button
                        button.setText("DOWNLOAD");
                        button.setEnabled(true);
                        break;
                      }
                    }
                  }
                }
              }
              
              // Find the bottom panel to update the label
              Component southComp = ((BorderLayout) installationPanel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
              if (southComp instanceof JPanel) {
                JPanel bottomPanel = (JPanel) southComp;
                Component westComp = ((BorderLayout) bottomPanel.getLayout()).getLayoutComponent(BorderLayout.WEST);
                
                if (westComp instanceof JPanel) {
                  JPanel leftPanel = (JPanel) westComp;
                  
                  // Remove any existing components (like the "Open Location" link)
                  leftPanel.removeAll();
                  
                  // No label needed after deletion
                  leftPanel.revalidate();
                  leftPanel.repaint();
                }
              }
              
              break;
            }
          }
        }
      }
    } catch (Exception ex) {
      LOG.warn("Error updating download button after deletion: " + ex.getMessage(), ex);
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
  
  private JButton createButton(String text, Project project, String message) {
    JButton button = new JButton(text);
    button.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            LOG.warn(text + " button clicked");
            com.intellij.openapi.ui.Messages.showInfoMessage(project, message, "MyLocalTon Plugin");
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
              com.intellij.openapi.ui.Messages.showInfoMessage(
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
}
