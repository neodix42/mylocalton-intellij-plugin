package org.ton.mylocalton.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.*;
import javax.swing.border.TitledBorder;

/** Factory for creating the Demo Tool Window. */
public class MyLocalTonToolWindowFactory implements ToolWindowFactory {
  // Use static logger to avoid initialization issues
  private static final Logger LOG = Logger.getInstance(MyLocalTonToolWindowFactory.class);

  // Status label for download
  private JLabel downloadedLabel;

  static {
    LOG.warn("DemoToolWindowFactory class loaded");
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
      mainPanel.add(installationPanel);
      mainPanel.add(Box.createVerticalStrut(10)); // Add spacing

      // 2. Startup settings Section
      JPanel startupSettingsPanel = createStartupSettingsPanel(project);
      mainPanel.add(startupSettingsPanel);
      mainPanel.add(Box.createVerticalStrut(10)); // Add spacing

      // 3. Actions Section
      JPanel actionsPanel = createActionsPanel(project);
      mainPanel.add(actionsPanel);
      mainPanel.add(Box.createVerticalStrut(10)); // Add spacing

      // 4. Uninstall Section
      JPanel uninstallPanel = createUninstallPanel(project);
      mainPanel.add(uninstallPanel);

      // Add the panel to the tool window
      ContentFactory contentFactory = ContentFactory.getInstance();
      com.intellij.ui.content.Content content =
          contentFactory.createContent(mainPanel, "MyLocalTon", false);
      toolWindow.getContentManager().addContent(content);

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

    // Download button
    JButton downloadButton = new JButton("DOWNLOAD");
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

                              // Replace the label with a clickable link to open the download
                              // location
                              downloadedLabel.setText("");
                              JPanel parentPanel = (JPanel) downloadedLabel.getParent();
                              parentPanel.remove(downloadedLabel);

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
                              parentPanel.add(openLocationLink);
                              parentPanel.revalidate();
                              parentPanel.repaint();

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
                              downloadedLabel.setText("Download failed.");
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
    downloadedLabel = new JLabel("Ready to download.");
    leftPanel.add(downloadedLabel);
    bottomPanel.add(leftPanel, BorderLayout.WEST);

    // Add Testnet checkbox to the bottom right
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JCheckBox testnetCheckbox = new JCheckBox("Testnet");
    testnetCheckbox.setToolTipText("Enable testnet mode for installation");
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
    JPanel checkboxPanel = new JPanel(new GridLayout(0, 2, 10, 5));

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
    
//    listbox.addActionListener(new ActionListener() {
//        @Override
//        public void actionPerformed(ActionEvent e) {
//            Integer selectedValue = (Integer) listbox.getSelectedItem();
//            com.intellij.openapi.ui.Messages.showInfoMessage(
//                project, "Selected value: " + selectedValue, "MyLocalTon Plugin");
//        }
//    });
    
    // Add the listbox to the panel
    listboxPanel.add(listbox);
    
    // Add the "add validators" label next to the listbox
    JLabel validatorsLabel = new JLabel("add validators");
    listboxPanel.add(validatorsLabel);
    
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
    JButton startButton = createButton("Start", project, "Start operation initiated!");
    JButton stopButton = createButton("Stop", project, "Stop operation initiated!");
    JButton resetButton = createButton("Reset", project, "Reset operation initiated!");

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
    JLabel statusLabel = new JLabel("Status: Ready");
    rightPanel.add(statusLabel);
    southPanel.add(rightPanel, BorderLayout.EAST);

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
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project, "Delete operation initiated!", "MyLocalTon Plugin");
          }
        });

    // Center the button
    JPanel buttonPanel = new JPanel();
    buttonPanel.add(deleteButton);
    panel.add(buttonPanel, BorderLayout.CENTER);

    // Add label below Delete button, aligned to the right
    JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JLabel uninstallStatusLabel = new JLabel("Status: Not uninstalled");
    southPanel.add(uninstallStatusLabel);
    panel.add(southPanel, BorderLayout.SOUTH);

    return panel;
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
