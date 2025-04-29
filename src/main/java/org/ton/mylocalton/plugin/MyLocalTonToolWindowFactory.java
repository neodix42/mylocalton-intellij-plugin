package org.ton.mylocalton.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Factory for creating the Demo Tool Window.
 */
public class MyLocalTonToolWindowFactory implements ToolWindowFactory {
    // Use static logger to avoid initialization issues
    private static final Logger LOG = Logger.getInstance(MyLocalTonToolWindowFactory.class);
    
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
            com.intellij.ui.content.Content content = contentFactory.createContent(mainPanel, "MyLocalTon", false);
            toolWindow.getContentManager().addContent(content);
            
            LOG.warn("Tool window content created successfully");
        } catch (Exception e) {
            LOG.warn("Error creating tool window content: " + e.getMessage(), e);
        }
    }
    
    private JPanel createInstallationPanel(Project project) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
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
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LOG.warn("Download button clicked");
                com.intellij.openapi.ui.Messages.showInfoMessage(project, "Download initiated!", "MyLocalTon Plugin");
                
                // Make progress bar visible when Download button is clicked
                progressBar.setVisible(true);
                
                // Simulate progress (just for demonstration)
                new Thread(() -> {
                    try {
                        for (int i = 0; i <= 100; i += 10) {
                            final int value = i;
                            SwingUtilities.invokeLater(() -> progressBar.setValue(value));
                            Thread.sleep(500); // Simulate work being done
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
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
        
        // Add "Downloaded." label to the bottom left
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel downloadedLabel = new JLabel("Downloaded.");
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
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Startup settings",
                TitledBorder.LEFT,
                TitledBorder.TOP));
        
        // Create a grid layout for checkboxes
        JPanel checkboxPanel = new JPanel(new GridLayout(3, 2, 10, 5));
        
        // Add 6 Testnet checkboxes with tooltips
        String[] tooltips = {
            "Enable testnet mode for network 1",
            "Enable testnet mode for network 2",
            "Enable testnet mode for network 3",
            "Enable testnet mode for network 4",
            "Enable testnet mode for network 5",
            "Enable testnet mode for network 6"
        };
        
        for (int i = 0; i < 6; i++) {
            JCheckBox checkbox = new JCheckBox("Testnet");
            checkbox.setToolTipText(tooltips[i]);
            checkboxPanel.add(checkbox);
        }
        
        panel.add(checkboxPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createActionsPanel(Project project) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
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
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Uninstall",
                TitledBorder.LEFT,
                TitledBorder.TOP));
        
        // Delete button
        JButton deleteButton = new JButton("Delete");
        deleteButton.setPreferredSize(new Dimension(150, 30));
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LOG.warn("Delete button clicked");
                com.intellij.openapi.ui.Messages.showInfoMessage(project, "Delete operation initiated!", "MyLocalTon Plugin");
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
        button.addActionListener(new ActionListener() {
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
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                LOG.warn(text + " link clicked");
                com.intellij.openapi.ui.Messages.showInfoMessage(project, message, "MyLocalTon Plugin");
            }
        });
        return link;
    }
}
