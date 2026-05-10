package com.pinnacle.mcstats;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class MinecraftServerStatsApp extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final AppData data;
    private final DataStore dataStore;
    private McServer currentServer;
    private Member currentMember;
    private boolean deleteMode = false;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public MinecraftServerStatsApp() {
        super("Minecraft Server Stats Manager");
        this.dataStore = new DataStore();
        this.data = dataStore.load();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 650);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
        setContentPane(root);
        showServerSelect();
    }

    private void setScreen(String key, JPanel panel) {
        root.add(panel, key);
        cards.show(root, key);
        revalidate();
        repaint();
    }

    private JPanel screenBase(String title) {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));
        JLabel header = new JLabel(title, SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 28f));
        panel.add(header, BorderLayout.NORTH);
        return panel;
    }

    private void showServerSelect() {
        currentServer = null;
        currentMember = null;
        JPanel panel = screenBase(deleteMode ? "Select a Server to Delete" : "Minecraft Servers");

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(20, 60, 20, 60));

        if (data.servers.isEmpty()) {
            JLabel empty = new JLabel("No Servers Created", SwingConstants.CENTER);
            empty.setFont(empty.getFont().deriveFont(Font.PLAIN, 22f));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            list.add(Box.createVerticalGlue());
            list.add(empty);
            list.add(Box.createVerticalGlue());
        } else {
            for (McServer server : data.servers) {
                JButton button = wideButton(server.name);
                button.addActionListener(e -> {
                    if (deleteMode) {
                        int confirm = JOptionPane.showConfirmDialog(this,
                                "Delete server '" + server.name + "'? This also deletes its members and stats.",
                                "Delete Server", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            data.servers.remove(server);
                            saveData();
                            deleteMode = false;
                            showServerSelect();
                        }
                    } else {
                        currentServer = server;
                        showServerPage(server);
                    }
                });
                list.add(button);
                list.add(Box.createVerticalStrut(12));
            }
        }

        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton exit = new JButton("Exit");
        exit.addActionListener(e -> dispose());
        bottom.add(exit, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton add = new JButton("+");
        add.setFont(add.getFont().deriveFont(Font.BOLD, 22f));
        add.addActionListener(e -> { deleteMode = false; showAddServer(); });
        JButton remove = new JButton(deleteMode ? "Cancel -" : "-");
        remove.setFont(remove.getFont().deriveFont(Font.BOLD, 22f));
        remove.addActionListener(e -> { deleteMode = !deleteMode; showServerSelect(); });
        right.add(add);
        right.add(remove);
        bottom.add(right, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);

        setScreen("serverSelect", panel);
    }

    private JButton wideButton(String text) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        button.setPreferredSize(new Dimension(500, 54));
        return button;
    }

    private void showAddServer() {
        JPanel panel = screenBase("Add New Server");
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel nameLabel = new JLabel("Server Name:");
        JTextField nameField = new JTextField(28);
        JButton save = new JButton("Save");

        gc.gridx = 0; gc.gridy = 0; form.add(nameLabel, gc);
        gc.gridx = 1; gc.gridy = 0; form.add(nameField, gc);
        gc.gridx = 1; gc.gridy = 1; form.add(save, gc);

        save.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a server name.");
                return;
            }
            data.servers.add(new McServer(UUID.randomUUID().toString(), name));
            saveData();
            showServerSelect();
        });

        panel.add(form, BorderLayout.CENTER);
        panel.add(backOnlyBottom(this::showServerSelect), BorderLayout.SOUTH);
        setScreen("addServer", panel);
    }

    private void showServerPage(McServer server) {
        currentServer = server;
        JPanel panel = screenBase(server.name);
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(20, 60, 20, 60));

        if (server.members.isEmpty()) {
            JLabel empty = new JLabel("No Members Created", SwingConstants.CENTER);
            empty.setFont(empty.getFont().deriveFont(Font.PLAIN, 22f));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            list.add(Box.createVerticalGlue());
            list.add(empty);
            list.add(Box.createVerticalGlue());
        } else {
            for (Member member : server.members) {
                JButton button = wideButton(member.name);
                button.addActionListener(e -> showMemberPage(server, member));
                list.add(button);
                list.add(Box.createVerticalStrut(12));
            }
        }
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton back = new JButton("Back");
        back.addActionListener(e -> showServerSelect());
        bottom.add(back, BorderLayout.WEST);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton updateStats = new JButton("Update Member Stats");
        updateStats.addActionListener(e -> showBulkUpdateStats(server));
        JButton addMember = new JButton("Add New Member");
        addMember.addActionListener(e -> showAddMember(server));
        rightButtons.add(updateStats);
        rightButtons.add(addMember);
        bottom.add(rightButtons, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        setScreen("serverPage", panel);
    }

    private JPanel backOnlyBottom(Runnable backAction) {
        JPanel bottom = new JPanel(new BorderLayout());
        JButton back = new JButton("Back");
        back.addActionListener(e -> backAction.run());
        bottom.add(back, BorderLayout.WEST);
        return bottom;
    }

    private void showBulkUpdateStats(McServer server) {
        JPanel panel = screenBase("Update Member Stats");

        JPanel content = new JPanel(new BorderLayout(12, 12));
        JLabel instructions = new JLabel("Drop one or more Minecraft player .json stat files here. Filenames must include the player UUID.", SwingConstants.CENTER);
        instructions.setFont(instructions.getFont().deriveFont(Font.PLAIN, 16f));
        content.add(instructions, BorderLayout.NORTH);

        JTextArea report = new JTextArea();
        report.setEditable(false);
        report.setLineWrap(true);
        report.setWrapStyleWord(true);
        report.setText("Waiting for .json files...\n\nExample filename: 12345678-1234-1234-1234-123456789abc.json");
        DefaultListModel<File> queuedFiles = new DefaultListModel<>();
        JList<File> fileList = new JList<>(queuedFiles);
        fileList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.getName() + "  (" + value.getAbsolutePath() + ")");
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("No files queued");

        JPanel dropZone = new JPanel(new BorderLayout());
        dropZone.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2f, 8f, 6f, true));
        JLabel dropLabel = new JLabel("Drag and drop .json files here", SwingConstants.CENTER);
        dropLabel.setFont(dropLabel.getFont().deriveFont(Font.BOLD, 22f));
        dropZone.add(dropLabel, BorderLayout.CENTER);
        dropZone.setPreferredSize(new Dimension(500, 180));

        dropZone.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    addFilesToQueue(queuedFiles, files);
                    progressBar.setValue(0);
                    progressBar.setString(queuedFiles.isEmpty() ? "No files queued" : queuedFiles.size() + " file(s) queued");
                    return true;
                } catch (Exception ex) {
                    report.setText("Could not read dropped files:\n" + ex.getMessage());
                    return false;
                }
            }
        });

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.add(dropZone, BorderLayout.NORTH);
        center.add(new JScrollPane(fileList), BorderLayout.CENTER);
        JPanel reportAndProgress = new JPanel(new BorderLayout(8, 8));
        reportAndProgress.add(progressBar, BorderLayout.NORTH);
        reportAndProgress.add(new JScrollPane(report), BorderLayout.CENTER);
        center.add(reportAndProgress, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton back = new JButton("Back");
        back.addActionListener(e -> showServerPage(server));
        bottom.add(back, BorderLayout.WEST);

        JButton chooseFiles = new JButton("Choose .json Files");
        chooseFiles.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("Minecraft stat JSON files", "json"));
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                addFilesToQueue(queuedFiles, Arrays.asList(chooser.getSelectedFiles()));
                progressBar.setValue(0);
                progressBar.setString(queuedFiles.isEmpty() ? "No files queued" : queuedFiles.size() + " file(s) queued");
            }
        });
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton process = new JButton("Process Queued Files");
        process.addActionListener(e -> {
            List<File> files = Collections.list(queuedFiles.elements());
            report.setText(updateStatsFromFiles(server, files, progressBar));
            queuedFiles.clear();
            progressBar.setString("Done");
        });
        rightActions.add(chooseFiles);
        rightActions.add(process);
        bottom.add(rightActions, BorderLayout.EAST);

        panel.add(bottom, BorderLayout.SOUTH);
        setScreen("bulkUpdateStats", panel);
    }

    private String updateStatsFromFiles(McServer server, List<File> files, JProgressBar progressBar) {
        if (files == null || files.isEmpty()) {
            return "No files were selected.";
        }

        int updated = 0;
        int skipped = 0;
        StringBuilder report = new StringBuilder();
        report.append("Update Results for ").append(server.name).append("\n");
        report.append("=".repeat(Math.max(24, server.name.length() + 19))).append("\n\n");

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            int pct = (int) (((double) i / Math.max(1, files.size())) * 100);
            progressBar.setValue(pct);
            progressBar.setString("Processing " + (i + 1) + " / " + files.size());
            if (file == null || !file.isFile()) {
                skipped++;
                report.append("Skipped: not a readable file.\n");
                continue;
            }

            String fileName = file.getName();
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                skipped++;
                report.append("Skipped ").append(fileName).append(": file is not a .json file.\n");
                continue;
            }

            String uuidFromFile = uuidFromFileName(fileName);
            if (uuidFromFile.isEmpty()) {
                skipped++;
                report.append("Skipped ").append(fileName).append(": no UUID could be read from the filename.\n");
                continue;
            }

            Member target = findMemberByUuid(server, uuidFromFile);
            if (target == null) {
                target = createMemberFromUuid(server, uuidFromFile);
                if (target == null) {
                    skipped++;
                    report.append("Skipped ").append(fileName).append(": no member on this server has UUID ").append(uuidFromFile)
                            .append(" and Mojang lookup failed.\n");
                    continue;
                }
                report.append("Created new member ").append(target.name).append(" (").append(target.uuid).append(") from Mojang profile lookup.\n");
            }

            try {
                String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (raw.isEmpty()) {
                    skipped++;
                    report.append("Skipped ").append(fileName).append(": file is empty.\n");
                    continue;
                }

                updateMemberStatsFromRawJson(target, raw);
                updated++;
                report.append("Updated ").append(target.name).append(" from ").append(fileName)
                        .append(" with ").append(target.stats.size()).append(" stat values.\n");
            } catch (Exception ex) {
                skipped++;
                report.append("Skipped ").append(fileName).append(": ").append(ex.getMessage()).append("\n");
            }
        }
        progressBar.setValue(100);

        if (updated > 0) {
            saveData();
        }

        report.append("\nSummary: ").append(updated).append(" updated, ").append(skipped).append(" skipped.");
        if (updated > 0) {
            report.append("\nStats are saved. Go back and open a member to see the updated table.");
        }
        return report.toString();
    }

    private void addFilesToQueue(DefaultListModel<File> queue, List<File> files) {
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                queue.addElement(file);
            }
        }
    }

    private String uuidFromFileName(String fileName) {
        String base = fileName;
        if (base.toLowerCase(Locale.ROOT).endsWith(".json")) {
            base = base.substring(0, base.length() - 5);
        }
        String cleaned = base.replaceAll("[^A-Fa-f0-9-]", "").toLowerCase(Locale.ROOT);
        String noHyphen = cleaned.replace("-", "");
        return noHyphen.length() == 32 ? noHyphen : "";
    }

    private Member findMemberByUuid(McServer server, String uuid) {
        String wanted = normalizeUuid(uuid);
        for (Member member : server.members) {
            if (normalizeUuid(member.uuid).equals(wanted)) {
                return member;
            }
        }
        return null;
    }

    private String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private void updateMemberStatsFromRawJson(Member member, String raw) {
        Object parsed = SimpleJson.parse(raw);
        LinkedHashMap<String, String> flattened = new LinkedHashMap<>();
        flattenJson("", parsed, flattened);
        member.rawStatsJson = raw;
        member.stats.clear();
        member.stats.putAll(flattened);
    }

    private void showAddMember(McServer server) {
        JPanel panel = screenBase("Add New Member");
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(28);
        JTextField uuidField = new JTextField(28);
        JButton save = new JButton("Save");

        gc.gridx = 0; gc.gridy = 0; form.add(new JLabel("Member Name:"), gc);
        gc.gridx = 1; gc.gridy = 0; form.add(nameField, gc);
        gc.gridx = 0; gc.gridy = 1; form.add(new JLabel("UUID:"), gc);
        gc.gridx = 1; gc.gridy = 1; form.add(uuidField, gc);
        gc.gridx = 1; gc.gridy = 2; form.add(save, gc);

        save.addActionListener(e -> {
            String name = nameField.getText().trim();
            String uuid = uuidField.getText().trim();
            if (name.isEmpty() || uuid.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter both a member name and UUID.");
                return;
            }
            server.members.add(new Member(UUID.randomUUID().toString(), name, uuid));
            saveData();
            showServerPage(server);
        });

        panel.add(form, BorderLayout.CENTER);
        panel.add(backOnlyBottom(() -> showServerPage(server)), BorderLayout.SOUTH);
        setScreen("addMember", panel);
    }

    private void showMemberPage(McServer server, Member member) {
        currentServer = server;
        currentMember = member;
        JPanel panel = screenBase(member.name);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        JPanel info = new JPanel(new GridLayout(0, 1, 8, 8));
        info.add(new JLabel("Member Name: " + member.name));
        info.add(new JLabel("UUID: " + member.uuid));
        info.add(new JLabel(member.stats.isEmpty() ? "Stats: No stats saved yet." : "Stats: " + member.stats.size() + " values loaded."));
        content.add(info, BorderLayout.NORTH);

        JTable table = new JTable();
        table.setAutoCreateRowSorter(true);
        table.setModel(statsTableModel(member));
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>((DefaultTableModel) table.getModel());
        table.setRowSorter(sorter);
        JTextField searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void apply() {
                String q = searchField.getText().trim();
                if (q.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(q), 0));
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
        });
        JPanel tableArea = new JPanel(new BorderLayout(8, 8));
        JPanel searchPanel = new JPanel(new BorderLayout(6, 6));
        searchPanel.add(new JLabel("Search Stat:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        tableArea.add(searchPanel, BorderLayout.NORTH);
        tableArea.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(tableArea, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton back = new JButton("Back");
        back.addActionListener(e -> showServerPage(server));
        bottom.add(back, BorderLayout.WEST);
        JButton inputStats = new JButton("Input Stats");
        inputStats.addActionListener(e -> showInputStats(server, member));
        bottom.add(inputStats, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        setScreen("memberPage", panel);
    }

    private DefaultTableModel statsTableModel(Member member) {
        DefaultTableModel model = new DefaultTableModel(new String[]{"Stat", "Value"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        if (member.stats.isEmpty()) {
            model.addRow(new Object[]{"No stats saved yet", "Paste Minecraft stats JSON using Input Stats"});
        } else {
            member.stats.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> model.addRow(new Object[]{prettyStatName(e.getKey()), e.getValue()}));
        }
        return model;
    }

    private String prettyStatName(String raw) {
        return raw.replace("stats.", "")
                .replace("minecraft:", "")
                .replace('.', ' ')
                .replace('_', ' ');
    }

    private void showInputStats(McServer server, Member member) {
        JPanel panel = screenBase("Input Stats for " + member.name);
        JTextArea jsonArea = new JTextArea(member.rawStatsJson == null ? "" : member.rawStatsJson);
        jsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        jsonArea.setLineWrap(true);
        jsonArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(jsonArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton back = new JButton("Back");
        back.addActionListener(e -> showMemberPage(server, member));
        bottom.add(back, BorderLayout.WEST);
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            String raw = jsonArea.getText().trim();
            if (raw.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please paste the player's .json stats first.");
                return;
            }
            try {
                updateMemberStatsFromRawJson(member, raw);
                saveData();
                JOptionPane.showMessageDialog(this, "Stats saved and updated for " + member.name + ".");
                showMemberPage(server, member);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, "That JSON could not be parsed:\n" + ex.getMessage(),
                        "Invalid JSON", JOptionPane.ERROR_MESSAGE);
            }
        });
        bottom.add(save, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        setScreen("inputStats", panel);
    }

    private void flattenJson(String path, Object value, LinkedHashMap<String, String> out) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String next = path.isEmpty() ? key : path + "." + key;
                flattenJson(next, entry.getValue(), out);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flattenJson(path + "[" + i + "]", list.get(i), out);
            }
        } else {
            out.put(path, String.valueOf(value));
        }
    }

    private void saveData() {
        try {
            dataStore.save(data);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save data:\n" + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Member createMemberFromUuid(McServer server, String normalizedUuid) {
        try {
            String mojangName = fetchMojangNameByUuid(normalizedUuid);
            if (mojangName == null || mojangName.isBlank()) {
                return null;
            }
            Member member = new Member(UUID.randomUUID().toString(), mojangName, normalizedUuid);
            server.members.add(member);
            return member;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchMojangNameByUuid(String uuid) throws IOException, InterruptedException {
        String clean = normalizeUuid(uuid);
        if (clean.length() != 32) return null;
        String url = "https://api.mojang.com/user/profile/" + URLEncoder.encode(clean, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        Object parsed = SimpleJson.parse(response.body());
        if (parsed instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null) return String.valueOf(name);
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MinecraftServerStatsApp().setVisible(true));
    }
}

class AppData {
    final List<McServer> servers = new ArrayList<>();
}

class McServer {
    final String id;
    String name;
    final List<Member> members = new ArrayList<>();

    McServer(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class Member {
    final String id;
    String name;
    String uuid;
    String rawStatsJson = "";
    final LinkedHashMap<String, String> stats = new LinkedHashMap<>();

    Member(String id, String name, String uuid) {
        this.id = id;
        this.name = name;
        this.uuid = uuid;
    }
}

class DataStore {
    private final Path dir = Path.of(System.getProperty("user.home"), ".mc-server-stats-manager");
    private final Path file = dir.resolve("data.json");

    AppData load() {
        AppData data = new AppData();
        if (!Files.exists(file)) return data;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Object parsed = SimpleJson.parse(json);
            if (!(parsed instanceof Map<?, ?> root)) return data;
            Object serversValue = root.get("servers");
            if (serversValue instanceof List<?> servers) {
                for (Object serverObj : servers) {
                    if (!(serverObj instanceof Map<?, ?> serverMap)) continue;
                    McServer server = new McServer(stringValue(serverMap.get("id")), stringValue(serverMap.get("name")));
                    Object membersValue = serverMap.get("members");
                    if (membersValue instanceof List<?> members) {
                        for (Object memberObj : members) {
                            if (!(memberObj instanceof Map<?, ?> memberMap)) continue;
                            Member member = new Member(stringValue(memberMap.get("id")), stringValue(memberMap.get("name")), stringValue(memberMap.get("uuid")));
                            member.rawStatsJson = stringValue(memberMap.get("rawStatsJson"));
                            Object statsValue = memberMap.get("stats");
                            if (statsValue instanceof Map<?, ?> statsMap) {
                                for (Map.Entry<?, ?> entry : statsMap.entrySet()) {
                                    member.stats.put(String.valueOf(entry.getKey()), stringValue(entry.getValue()));
                                }
                            }
                            server.members.add(member);
                        }
                    }
                    data.servers.add(server);
                }
            }
        } catch (Exception ignored) {
            // Start with empty data if the saved file was manually edited into an invalid state.
        }
        return data;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    void save(AppData data) throws IOException {
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"servers\": [\n");
        for (int i = 0; i < data.servers.size(); i++) {
            McServer s = data.servers.get(i);
            sb.append("    {\n");
            appendField(sb, "id", s.id, 6, true);
            appendField(sb, "name", s.name, 6, true);
            sb.append("      \"members\": [\n");
            for (int j = 0; j < s.members.size(); j++) {
                Member m = s.members.get(j);
                sb.append("        {\n");
                appendField(sb, "id", m.id, 10, true);
                appendField(sb, "name", m.name, 10, true);
                appendField(sb, "uuid", m.uuid, 10, true);
                appendField(sb, "rawStatsJson", m.rawStatsJson == null ? "" : m.rawStatsJson, 10, true);
                sb.append("          \"stats\": {");
                if (!m.stats.isEmpty()) sb.append('\n');
                int k = 0;
                for (Map.Entry<String, String> entry : m.stats.entrySet()) {
                    sb.append("            \"").append(escape(entry.getKey())).append("\": \"").append(escape(entry.getValue())).append("\"");
                    if (++k < m.stats.size()) sb.append(',');
                    sb.append('\n');
                }
                if (!m.stats.isEmpty()) sb.append("          ");
                sb.append("}\n");
                sb.append("        }");
                if (j + 1 < s.members.size()) sb.append(',');
                sb.append('\n');
            }
            sb.append("      ]\n");
            sb.append("    }");
            if (i + 1 < data.servers.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private void appendField(StringBuilder sb, String key, String value, int spaces, boolean comma) {
        sb.append(" ".repeat(spaces)).append("\"").append(key).append("\": \"").append(escape(value)).append("\"");
        if (comma) sb.append(',');
        sb.append('\n');
    }

    private String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}

class SimpleJson {
    private final String text;
    private int pos;

    private SimpleJson(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        SimpleJson parser = new SimpleJson(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.isEnd()) throw parser.error("Unexpected trailing text");
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (isEnd()) throw error("Unexpected end of JSON");
        char c = peek();
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (c == 't') return readLiteral("true", Boolean.TRUE);
        if (c == 'f') return readLiteral("false", Boolean.FALSE);
        if (c == 'n') return readLiteral("null", null);
        if (c == '-' || Character.isDigit(c)) return readNumber();
        throw error("Unexpected character '" + c + "'");
    }

    private Map<String, Object> readObject() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (tryRead('}')) return map;
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("Expected string key");
            String key = readString();
            skipWhitespace();
            expect(':');
            Object value = readValue();
            map.put(key, value);
            skipWhitespace();
            if (tryRead('}')) break;
            expect(',');
        }
        return map;
    }

    private List<Object> readArray() {
        ArrayList<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (tryRead(']')) return list;
        while (true) {
            list.add(readValue());
            skipWhitespace();
            if (tryRead(']')) break;
            expect(',');
        }
        return list;
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (!isEnd()) {
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (isEnd()) throw error("Unfinished escape sequence");
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw error("Invalid unicode escape");
                        String hex = text.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ex) {
                            throw error("Invalid unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("Invalid escape sequence \\" + esc + "");
                }
            } else {
                sb.append(c);
            }
        }
        throw error("Unterminated string");
    }

    private Object readLiteral(String literal, Object value) {
        if (text.startsWith(literal, pos)) {
            pos += literal.length();
            return value;
        }
        throw error("Expected " + literal);
    }

    private Number readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (!isEnd() && Character.isDigit(peek())) pos++;
        if (!isEnd() && peek() == '.') {
            pos++;
            while (!isEnd() && Character.isDigit(peek())) pos++;
        }
        if (!isEnd() && (peek() == 'e' || peek() == 'E')) {
            pos++;
            if (!isEnd() && (peek() == '+' || peek() == '-')) pos++;
            while (!isEnd() && Character.isDigit(peek())) pos++;
        }
        String number = text.substring(start, pos);
        try {
            if (number.contains(".") || number.contains("e") || number.contains("E")) return Double.parseDouble(number);
            return Long.parseLong(number);
        } catch (NumberFormatException ex) {
            throw error("Invalid number " + number);
        }
    }

    private void skipWhitespace() {
        while (!isEnd() && Character.isWhitespace(peek())) pos++;
    }

    private boolean tryRead(char c) {
        if (!isEnd() && peek() == c) { pos++; return true; }
        return false;
    }

    private void expect(char c) {
        if (isEnd() || text.charAt(pos) != c) throw error("Expected '" + c + "'");
        pos++;
    }

    private char peek() { return text.charAt(pos); }
    private boolean isEnd() { return pos >= text.length(); }
    private RuntimeException error(String message) { return new RuntimeException(message + " at position " + pos); }
}
