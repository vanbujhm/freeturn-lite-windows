package com.freeturn.lite;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FreeTurnLiteFrame extends JFrame {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private final CoreProcessRunner runner = new CoreProcessRunner();

    private final JTextArea shareUri = new JTextArea(2, 28);
    private final JTextField corePath = new JTextField();
    private final JTextField peer = new JTextField();
    private final JTextField subUrl = new JTextField();
    private final JTextArea links = new JTextArea(4, 28);
    private final JTextField listen = new JTextField();
    private final JComboBox<String> provider = new JComboBox<>(new String[]{"vk", "direct"});
    private final JComboBox<String> platform = new JComboBox<>(new String[]{"desktop", "mobile"});
    private final JSpinner threads = new JSpinner(new SpinnerNumberModel(12, 0, 256, 1));
    private final JSpinner streamsPerCred = new JSpinner(new SpinnerNumberModel(12, 0, 256, 1));
    private final JCheckBox tcpMode = new JCheckBox("TCP mode (-mode tcp)");
    private final JCheckBox bond = new JCheckBox("Bonding (-bond)");
    private final JCheckBox udpTransport = new JCheckBox("UDP TURN transport");
    private final JTextField kcpFlags = new JTextField();
    private final JCheckBox manualCaptcha = new JCheckBox("Manual captcha");
    private final JCheckBox advancedCaptcha = new JCheckBox("Advanced captcha");
    private final JCheckBox debug = new JCheckBox("Debug log");
    private final JCheckBox routes = new JCheckBox("Exclude TURN from VPN (-routes, needs admin)");
    private final JComboBox<String> dnsMode = new JComboBox<>(new String[]{"auto", "plain", "doh"});
    private final JTextField dnsServers = new JTextField();
    private final JComboBox<String> obfProfile = new JComboBox<>(new String[]{"none", "rtpopus", "rtpopus2", "rtpopus3"});
    private final JTextField obfKey = new JTextField();
    private final JSpinner obfTiming = new JSpinner(new SpinnerNumberModel(0, 0, 60, 5));
    private final JTextField clientId = new JTextField();
    private final JCheckBox useRawArgs = new JCheckBox("Raw arguments mode");
    private final JTextArea rawArgs = new JTextArea(3, 28);
    private final JTextArea wireGuardConfig = new JTextArea(5, 28);
    private final JTextField wireGuardName = new JTextField();
    private final JSpinner wireGuardMtu = new JSpinner(new SpinnerNumberModel(1280, 0, 1500, 1));
    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel("Остановлен");
    private final JLabel configPath = new JLabel(CoreConfig.CONFIG_FILE.toString());
    private final JButton start = new JButton("Start");
    private final JButton stop = new JButton("Stop");
    private final JButton save = new JButton("Save");
    private final JButton openCaptcha = new JButton("Open captcha");
    private String latestUrl = "";

    FreeTurnLiteFrame(String initialUri) {
        super("FreeTurnLite for Windows 1.1.0");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 760));
        setLocationByPlatform(true);
        setLayout(new BorderLayout(8, 8));
        add(topBar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        add(bottomBar(), BorderLayout.SOUTH);

        bindActions();
        applyConfig(CoreConfig.load());
        if (initialUri != null && initialUri.regionMatches(true, 0, "freeturn://", 0, "freeturn://".length())) {
            shareUri.setText(initialUri);
            importShareUri();
        }
        refreshRunningState();
        pack();
        fitToScreen();
    }

    private void fitToScreen() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), screen.width), Math.min(getHeight(), screen.height));
    }

    private JPanel topBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        status.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        panel.add(status, BorderLayout.WEST);
        panel.add(new JLabel("FreeTurn запускает локальный endpoint, WireGuard/Amnezia поднимается отдельно."), BorderLayout.CENTER);
        return panel;
    }

    private JSplitPane content() {
        JScrollPane form = new JScrollPane(formPanel());
        form.setBorder(BorderFactory.createEmptyBorder());
        form.getVerticalScrollBar().setUnitIncrement(16);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, form, logPanel());
        split.setResizeWeight(0.58);
        split.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        return split;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Core options"),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int row = 0;

        addLabel(panel, c, row, "freeturn:// URI");
        JPanel uriPanel = new JPanel(new BorderLayout(4, 0));
        JButton importUri = new JButton("Import");
        importUri.addActionListener(e -> importShareUri());
        shareUri.setLineWrap(true);
        uriPanel.add(new JScrollPane(shareUri), BorderLayout.CENTER);
        uriPanel.add(importUri, BorderLayout.EAST);
        addField(panel, c, row++, uriPanel);

        addLabel(panel, c, row, "Core exe");
        JPanel coreRow = new JPanel(new BorderLayout(4, 0));
        JButton browse = new JButton("Browse");
        browse.addActionListener(e -> browseCore());
        coreRow.add(corePath, BorderLayout.CENTER);
        coreRow.add(browse, BorderLayout.EAST);
        addField(panel, c, row++, coreRow);

        addLabel(panel, c, row, "Server (-peer)");
        addField(panel, c, row++, peer);
        addLabel(panel, c, row, "Subscription (-sub)");
        addField(panel, c, row++, subUrl);
        addLabel(panel, c, row, "VK links (-links)");
        addField(panel, c, row++, new JScrollPane(links));
        addLabel(panel, c, row, "Listen");
        addField(panel, c, row++, listen);

        addLabel(panel, c, row, "Provider / Platform");
        JPanel providerRow = new JPanel(new GridBagLayout());
        GridBagConstraints pc = new GridBagConstraints();
        pc.insets = new Insets(0, 0, 0, 6);
        pc.fill = GridBagConstraints.HORIZONTAL;
        pc.weightx = 0.35;
        providerRow.add(provider, pc);
        pc.gridx = 1;
        pc.weightx = 0.65;
        pc.insets = new Insets(0, 0, 0, 0);
        providerRow.add(platform, pc);
        addField(panel, c, row++, providerRow);

        addLabel(panel, c, row, "Threads / streams");
        JPanel threadRow = new JPanel(new GridBagLayout());
        GridBagConstraints tc = new GridBagConstraints();
        tc.insets = new Insets(0, 0, 0, 6);
        tc.fill = GridBagConstraints.HORIZONTAL;
        tc.weightx = 0.5;
        threadRow.add(threads, tc);
        tc.gridx = 1;
        tc.insets = new Insets(0, 0, 0, 0);
        threadRow.add(streamsPerCred, tc);
        addField(panel, c, row++, threadRow);

        addLabel(panel, c, row, "Mode");
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeRow.add(tcpMode);
        modeRow.add(bond);
        modeRow.add(udpTransport);
        addField(panel, c, row++, modeRow);

        addLabel(panel, c, row, "KCP flags (TCP)");
        addField(panel, c, row++, kcpFlags);

        addLabel(panel, c, row, "DNS");
        JPanel dnsRow = new JPanel(new BorderLayout(6, 0));
        dnsRow.add(dnsMode, BorderLayout.WEST);
        dnsRow.add(dnsServers, BorderLayout.CENTER);
        addField(panel, c, row++, dnsRow);

        addLabel(panel, c, row, "Obfuscation");
        JPanel obfRow = new JPanel(new BorderLayout(6, 0));
        JButton generate = new JButton("Generate key");
        generate.addActionListener(e -> obfKey.setText(generateHexKey()));
        obfRow.add(obfProfile, BorderLayout.WEST);
        obfRow.add(obfKey, BorderLayout.CENTER);
        JPanel obfControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        obfControls.add(labeled("Timing, ms", obfTiming));
        obfControls.add(generate);
        obfRow.add(obfControls, BorderLayout.EAST);
        addField(panel, c, row++, obfRow);

        addLabel(panel, c, row, "Client ID");
        addField(panel, c, row++, clientId);

        addLabel(panel, c, row, "Flags");
        JPanel flagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        flagRow.add(manualCaptcha);
        advancedCaptcha.setToolTipText("<html>Автоматическая капча берёт PoW из настоящего браузера:<br>"
                + "ядро открывает страницу проверки в браузере по умолчанию.<br>"
                + "Если не вышло, используется обычное авторешение.<br>"
                + "Не действует вместе с ручной капчей.</html>");
        flagRow.add(advancedCaptcha);
        flagRow.add(debug);
        flagRow.add(routes);
        addField(panel, c, row++, flagRow);

        addLabel(panel, c, row, "Raw args");
        JPanel rawPanel = new JPanel(new BorderLayout(4, 4));
        rawPanel.add(useRawArgs, BorderLayout.NORTH);
        rawPanel.add(new JScrollPane(rawArgs), BorderLayout.CENTER);
        addField(panel, c, row++, rawPanel);

        addLabel(panel, c, row, "WireGuard .conf");
        JPanel wgPanel = new JPanel(new BorderLayout(4, 4));
        wgPanel.add(new JScrollPane(wireGuardConfig), BorderLayout.CENTER);
        JPanel wgControls = new JPanel(new GridBagLayout());
        GridBagConstraints wc = new GridBagConstraints();
        wc.insets = new Insets(0, 0, 0, 6);
        wc.fill = GridBagConstraints.HORIZONTAL;
        wc.weightx = 1;
        wgControls.add(labeled("Tunnel name", wireGuardName), wc);
        wc.gridx = 1;
        wc.weightx = 0;
        wgControls.add(labeled("MTU", wireGuardMtu), wc);
        wgPanel.add(wgControls, BorderLayout.SOUTH);
        addField(panel, c, row, wgPanel);
        return panel;
    }

    private JScrollPane logPanel() {
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createTitledBorder("Core log"));
        scroll.setPreferredSize(new Dimension(0, 180));
        return scroll;
    }

    private JPanel bottomBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        configPath.setToolTipText("Config path");
        panel.add(configPath, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton copyWireGuard = new JButton("Copy WG config");
        JButton saveWireGuard = new JButton("Save WG config");
        JButton clear = new JButton("Clear log");
        copyWireGuard.addActionListener(e -> copyWireGuardConfig());
        saveWireGuard.addActionListener(e -> saveWireGuardConfig());
        clear.addActionListener(e -> log.setText(""));
        openCaptcha.setEnabled(false);
        buttons.add(openCaptcha);
        buttons.add(copyWireGuard);
        buttons.add(saveWireGuard);
        buttons.add(clear);
        buttons.add(save);
        buttons.add(start);
        buttons.add(stop);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private void bindActions() {
        save.addActionListener(e -> saveConfig());
        start.addActionListener(e -> startCore());
        stop.addActionListener(e -> {
            appendLog("Остановка запрошена пользователем");
            runner.stop();
            refreshRunningState();
        });
        openCaptcha.addActionListener(e -> openUrl(latestUrl));
        useRawArgs.addActionListener(e -> updateRawMode());
        tcpMode.addActionListener(e -> updateRawMode());
        manualCaptcha.addActionListener(e -> updateRawMode());
        provider.addActionListener(e -> {
            // 0 оставляет число потоков ядру: для direct это 1.
            if (isDirect()) threads.setValue(0);
            updateRawMode();
        });
        new Timer(500, e -> refreshRunningState()).start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (runner.isRunning()) {
                    int choice = JOptionPane.showConfirmDialog(
                            FreeTurnLiteFrame.this,
                            "Ядро еще запущено. Остановить и выйти?",
                            "FreeTurnLite",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (choice != JOptionPane.YES_OPTION) return;
                }
                dispose();
                runner.shutdown();
                System.exit(0);
            }
        });
    }

    private void addLabel(JPanel panel, GridBagConstraints c, int row, String text) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(text), c);
    }

    private void addField(JPanel panel, GridBagConstraints c, int row, java.awt.Component field) {
        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.CENTER;
        panel.add(field, c);
    }

    private JPanel labeled(String label, java.awt.Component field) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private void browseCore() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Windows executables", "exe"));
        chooser.setDialogTitle("Select free-turn-proxy.exe");
        if (!corePath.getText().isBlank()) chooser.setSelectedFile(new java.io.File(corePath.getText()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            corePath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void importShareUri() {
        String raw = shareUri.getText();
        if (raw.isBlank()) {
            showError("Вставьте freeturn:// ссылку.");
            return;
        }
        try {
            CoreConfig config = readConfig();
            FreeturnUri.applyTo(config, raw);
            applyConfig(config);
            config.save();
            appendLog("freeturn:// imported: peer=" + config.peer + ", listen=" + config.listen);
            if ("vk".equals(config.provider) && config.links.isBlank()) {
                appendLog("VK call link не входит в эту ссылку, вставьте его в VK links.");
            }
            if (config.wireGuardConfig.isBlank()) appendLog("WireGuard config не найден в URI, его можно вставить отдельно.");
        } catch (Exception e) {
            showError("Не удалось импортировать freeturn://: " + e.getMessage());
        }
    }

    private void applyConfig(CoreConfig config) {
        corePath.setText(config.corePath);
        peer.setText(config.peer);
        subUrl.setText(config.subUrl);
        links.setText(config.links);
        listen.setText(config.listen);
        provider.setSelectedItem(config.provider);
        platform.setSelectedItem(config.platform);
        threads.setValue(config.threads);
        streamsPerCred.setValue(config.streamsPerCred);
        tcpMode.setSelected(config.tcpMode);
        bond.setSelected(config.bond);
        udpTransport.setSelected(config.udpTransport);
        kcpFlags.setText(config.kcpFlags);
        manualCaptcha.setSelected(config.manualCaptcha);
        advancedCaptcha.setSelected(config.advancedCaptcha);
        debug.setSelected(config.debug);
        routes.setSelected(config.routes);
        dnsMode.setSelectedItem(config.dnsMode);
        dnsServers.setText(config.dnsServers);
        obfProfile.setSelectedItem(config.obfProfile);
        obfKey.setText(config.obfKey);
        obfTiming.setValue(config.obfTimingMs);
        clientId.setText(config.clientId);
        useRawArgs.setSelected(config.useRawArgs);
        rawArgs.setText(config.rawArgs);
        wireGuardConfig.setText(config.wireGuardConfig);
        wireGuardName.setText(config.wireGuardName);
        wireGuardMtu.setValue(config.wireGuardMtu);
        updateRawMode();
    }

    private CoreConfig readConfig() {
        CoreConfig config = new CoreConfig();
        config.corePath = corePath.getText();
        config.peer = peer.getText();
        config.subUrl = subUrl.getText();
        config.links = links.getText();
        config.listen = listen.getText();
        config.provider = selected(provider);
        config.platform = selected(platform);
        config.threads = (Integer) threads.getValue();
        config.streamsPerCred = (Integer) streamsPerCred.getValue();
        config.tcpMode = tcpMode.isSelected();
        config.bond = bond.isSelected();
        config.udpTransport = udpTransport.isSelected();
        config.kcpFlags = kcpFlags.getText();
        config.manualCaptcha = manualCaptcha.isSelected();
        config.advancedCaptcha = advancedCaptcha.isSelected();
        config.debug = debug.isSelected();
        config.routes = routes.isSelected();
        config.dnsMode = selected(dnsMode);
        config.dnsServers = dnsServers.getText();
        config.obfProfile = selected(obfProfile);
        config.obfKey = obfKey.getText();
        config.obfTimingMs = (Integer) obfTiming.getValue();
        config.clientId = clientId.getText();
        config.useRawArgs = useRawArgs.isSelected();
        config.rawArgs = rawArgs.getText();
        config.wireGuardConfig = wireGuardConfig.getText();
        config.wireGuardName = wireGuardName.getText().trim().isEmpty() ? "freeturn-wg" : wireGuardName.getText().trim();
        config.wireGuardMtu = (Integer) wireGuardMtu.getValue();
        return config;
    }

    private void saveConfig() {
        try {
            readConfig().save();
            appendLog("Настройки сохранены: " + CoreConfig.CONFIG_FILE);
        } catch (Exception e) {
            showError("Не удалось сохранить настройки: " + e.getMessage());
        }
    }

    private void startCore() {
        CoreConfig config = readConfig();
        String validation = config.validate();
        if (!validation.isBlank()) {
            showError(validation);
            return;
        }
        try {
            config.save();
        } catch (Exception e) {
            showError("Не удалось сохранить настройки: " + e.getMessage());
            return;
        }
        runner.start(config, new CoreProcessRunner.Listener() {
            @Override
            public void onLine(String line) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(line);
                    maybeCaptureUrl(line);
                });
            }

            @Override
            public void onExit(int exitCode) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("Процесс завершен, код " + exitCode);
                    refreshRunningState();
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(message);
                    showError(message);
                    refreshRunningState();
                });
            }
        });
        refreshRunningState();
    }

    private boolean isDirect() {
        return "direct".equals(selected(provider));
    }

    private void updateRawMode() {
        boolean raw = useRawArgs.isSelected();
        boolean vkOptions = !raw && !isDirect();
        rawArgs.setEnabled(raw);
        peer.setEnabled(!raw);
        subUrl.setEnabled(!raw);
        links.setEnabled(vkOptions);
        listen.setEnabled(!raw);
        provider.setEnabled(!raw);
        platform.setEnabled(vkOptions);
        threads.setEnabled(!raw);
        streamsPerCred.setEnabled(vkOptions);
        tcpMode.setEnabled(!raw);
        bond.setEnabled(!raw && tcpMode.isSelected());
        kcpFlags.setEnabled(!raw && tcpMode.isSelected());
        udpTransport.setEnabled(vkOptions);
        manualCaptcha.setEnabled(vkOptions);
        advancedCaptcha.setEnabled(vkOptions && !manualCaptcha.isSelected());
        debug.setEnabled(!raw);
        routes.setEnabled(!raw);
        dnsMode.setEnabled(!raw);
        dnsServers.setEnabled(!raw);
        obfProfile.setEnabled(!raw);
        obfKey.setEnabled(!raw);
        obfTiming.setEnabled(!raw);
        clientId.setEnabled(!raw);
    }

    private void copyWireGuardConfig() {
        CoreConfig config = readConfig();
        String validation = config.validateWireGuardExport();
        if (!validation.isBlank()) {
            showError(validation);
            return;
        }
        try {
            config.save();
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(config.wireGuardExport()), null);
            appendLog("WireGuard config copied to clipboard. Endpoint = " + config.listen.trim());
            appendWireGuardRoutingWarning();
        } catch (Exception e) {
            showError("Не удалось скопировать WireGuard config: " + e.getMessage());
        }
    }

    private void saveWireGuardConfig() {
        CoreConfig config = readConfig();
        String validation = config.validateWireGuardExport();
        if (!validation.isBlank()) {
            showError(validation);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save WireGuard config");
        chooser.setSelectedFile(new java.io.File(config.wireGuardName + ".conf"));
        chooser.setFileFilter(new FileNameExtensionFilter("WireGuard config", "conf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            config.save();
            Files.writeString(chooser.getSelectedFile().toPath(), config.wireGuardExport(), StandardCharsets.UTF_8);
            appendLog("WireGuard config saved: " + chooser.getSelectedFile().getAbsolutePath());
            appendWireGuardRoutingWarning();
        } catch (Exception e) {
            showError("Не удалось сохранить WireGuard config: " + e.getMessage());
        }
    }

    private void appendWireGuardRoutingWarning() {
        appendLog("Важно: исключите free-turn-proxy.exe из туннеля во внешнем VPN-клиенте или включите -routes, "
                + "иначе возможна петля маршрутизации.");
    }

    private void refreshRunningState() {
        boolean running = runner.isRunning();
        status.setText(running ? "Запущен" : "Остановлен");
        start.setEnabled(!running);
        stop.setEnabled(running);
    }

    private void appendLog(String line) {
        log.append(line);
        log.append(System.lineSeparator());
        log.setCaretPosition(log.getDocument().getLength());
    }

    private void maybeCaptureUrl(String line) {
        Matcher matcher = URL_PATTERN.matcher(line);
        if (matcher.find()) {
            latestUrl = matcher.group();
            while (latestUrl.endsWith(".") || latestUrl.endsWith(",") || latestUrl.endsWith(")")) {
                latestUrl = latestUrl.substring(0, latestUrl.length() - 1);
            }
            openCaptcha.setEnabled(true);
            openCaptcha.setToolTipText(latestUrl);
        }
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            showError("Не удалось открыть URL: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "FreeTurnLite", JOptionPane.ERROR_MESSAGE);
    }

    private static String selected(JComboBox<String> comboBox) {
        Object selected = comboBox.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    private static String generateHexKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
