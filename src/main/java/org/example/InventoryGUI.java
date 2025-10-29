package org.example;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.util.Vector;
import java.util.regex.Pattern;

public class InventoryGUI extends JFrame {

    private JTable table;
    private DefaultTableModel model;
    private JLabel statusBar;
    private TableRowSorter<DefaultTableModel> sorter;
    private boolean darkMode = false;

    public InventoryGUI() {
        super("🧾 Smart Inventory Manager");

        // === Iconiță custom ===
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/icon.png"));
            setIconImage(icon.getImage());
        } catch (Exception e) {
            System.err.println("⚠️ Nu pot încărca iconița: " + e.getMessage());
        }

        // === Tema inițială ===
        try {
            UIManager.setLookAndFeel(new FlatMacLightLaf());
        } catch (Exception ignored) {}

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(950, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 20));

        // === Confirmare la închidere ===
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                        InventoryGUI.this,
                        "Sigur vrei să închizi aplicația?",
                        "Confirmare ieșire",
                        JOptionPane.YES_NO_OPTION
                );
                if (confirm == JOptionPane.YES_OPTION) dispose();
            }
        });

        // === Meniu sus ===
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("📁 Fișier");

        JMenuItem importItem = new JMenuItem("Import Excel...");
        JMenuItem exportItem = new JMenuItem("Export Excel...");
        JMenuItem exitItem = new JMenuItem("Ieșire");

        importItem.addActionListener(e -> importFromExcel());
        exportItem.addActionListener(e -> exportToExcel());
        exitItem.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        fileMenu.add(importItem);
        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // === Model + tabel ===
        model = new DefaultTableModel(new String[]{"ID", "Nume", "Cantitate", "Preț"}, 0);
        table = new JTable(model);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // === Editare directă cu update în DB ===
        model.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row >= 0 && col >= 0) updateDatabase(row);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new EmptyBorder(10, 20, 20, 20));
        add(scrollPane, BorderLayout.CENTER);

        // === Bara de control sus ===
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)),
                new EmptyBorder(15, 25, 15, 25)
        ));
        topPanel.setBackground(UIManager.getColor("Panel.background"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 0, 10);
        gbc.anchor = GridBagConstraints.CENTER;

        // === Căutare ===
        JTextField searchField = new JTextField(20);
        searchField.putClientProperty("JTextField.placeholderText", "🔍 Caută produse...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(searchField.getText()); }
            public void removeUpdate(DocumentEvent e) { filter(searchField.getText()); }
            public void changedUpdate(DocumentEvent e) { filter(searchField.getText()); }
        });
        gbc.gridx = 0;
        topPanel.add(searchField, gbc);

        // === Butoane ===
        JButton addBtn = new JButton("➕ Adaugă");
        JButton delBtn = new JButton("🗑️ Șterge");
        JButton chartBtn = new JButton("📊 Grafic");
        JButton refreshBtn = new JButton("🔄 Refresh");
        JButton themeBtn = new JButton("🌙");

        JButton[] buttons = {addBtn, delBtn, chartBtn, refreshBtn};
        for (JButton btn : buttons) {
            btn.setPreferredSize(new Dimension(120, 32));
            btn.setFocusPainted(false);
        }

        addBtn.addActionListener(e -> addRow());
        delBtn.addActionListener(e -> deleteRow());
        chartBtn.addActionListener(e -> new StockChart(this));
        refreshBtn.addActionListener(e -> loadFromDatabase());
        themeBtn.addActionListener(e -> toggleTheme(themeBtn));

        gbc.gridx = 1; topPanel.add(addBtn, gbc);
        gbc.gridx = 2; topPanel.add(delBtn, gbc);
        gbc.gridx = 3; topPanel.add(chartBtn, gbc);
        gbc.gridx = 4; topPanel.add(refreshBtn, gbc);
        gbc.gridx = 5; topPanel.add(themeBtn, gbc);

        add(topPanel, BorderLayout.NORTH);

        // === Bara de status ===
        statusBar = new JLabel("Produse: 0");
        statusBar.setBorder(new EmptyBorder(6, 10, 6, 10));
        add(statusBar, BorderLayout.SOUTH);

        loadFromDatabase();
        setVisible(true);
    }

    // === Reîncărcare din DB ===
    private void loadFromDatabase() {
        try (Connection c = Db.get();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM products")) {

            model.setRowCount(0);
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("qty"),
                        rs.getDouble("price")
                });
            }
            updateStatus();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Eroare la încărcare din DB: " + e.getMessage());
        }
    }

    // === Update automat în DB după editare ===
    private void updateDatabase(int row) {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE products SET name=?, qty=?, price=? WHERE id=?")) {

            ps.setString(1, model.getValueAt(row, 1).toString());
            ps.setInt(2, Integer.parseInt(model.getValueAt(row, 2).toString()));
            ps.setDouble(3, Double.parseDouble(model.getValueAt(row, 3).toString()));
            ps.setInt(4, Integer.parseInt(model.getValueAt(row, 0).toString()));
            ps.executeUpdate();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Eroare la actualizare DB: " + e.getMessage());
        }
    }

    private void addRow() {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO products(name, qty, price) VALUES('',0,0)",
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    model.addRow(new Object[]{rs.getInt(1), "", 0, 0.0});
                    updateStatus();
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Eroare la adăugare: " + e.getMessage());
        }
    }

    private void deleteRow() {
        int row = table.getSelectedRow();
        if (row == -1) return;
        int id = (int) model.getValueAt(row, 0);
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            model.removeRow(row);
            updateStatus();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Eroare la ștergere: " + e.getMessage());
        }
    }

    private void filter(String text) {
        if (text.trim().isEmpty()) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private void updateStatus() {
        statusBar.setText("Produse: " + model.getRowCount());
    }

    private void toggleTheme(JButton themeBtn) {
        FlatAnimatedLafChange.showSnapshot();
        try {
            if (darkMode) {
                FlatMacLightLaf.setup();
                themeBtn.setText("🌙");
            } else {
                FlatMacDarkLaf.setup();
                themeBtn.setText("☀️");
            }
            darkMode = !darkMode;
            SwingUtilities.updateComponentTreeUI(this);
        } finally {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        }
    }

    // === Import / Export Excel ===
    private void importFromExcel() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileInputStream fis = new FileInputStream(file);
                 Workbook workbook = new XSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0);
                model.setRowCount(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    Vector<Object> data = new Vector<>();
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING -> data.add(cell.getStringCellValue());
                            case NUMERIC -> data.add(cell.getNumericCellValue());
                            default -> data.add("");
                        }
                    }
                    model.addRow(data);
                }
                updateStatus();
                JOptionPane.showMessageDialog(this, "Import reușit din " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Eroare import: " + e.getMessage());
            }
        }
    }

    private void exportToExcel() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".xlsx"))
                file = new File(file.getAbsolutePath() + ".xlsx");

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Inventar");
                Row header = sheet.createRow(0);
                for (int i = 0; i < model.getColumnCount(); i++)
                    header.createCell(i).setCellValue(model.getColumnName(i));

                for (int i = 0; i < model.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < model.getColumnCount(); j++) {
                        Object value = model.getValueAt(i, j);
                        row.createCell(j).setCellValue(value != null ? value.toString() : "");
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    workbook.write(fos);
                }

                JOptionPane.showMessageDialog(this, "Datele au fost salvate în " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Eroare la export: " + e.getMessage());
            }
        }
    }

    public JTable getTable() {
        return table;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(InventoryGUI::new);
    }
}
