package org.example;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.SwingWrapper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockChart {

    public StockChart(InventoryGUI parent) {
        DefaultTableModel model = (DefaultTableModel) parent.getTable().getModel();

        // === Alegere tip grafic ===
        String[] options = {"Cantitate", "Preț", "Valoare totală (Cantitate × Preț)"};
        String choice = (String) JOptionPane.showInputDialog(
                parent,
                "Ce dorești să vizualizezi în grafic?",
                "Tip grafic",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == null) return;

        List<String> names = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        Pattern numberPattern = Pattern.compile("(\\d+(\\.\\d+)?)");

        // === Extragem datele din tabel ===
        for (int i = 0; i < model.getRowCount(); i++) {
            Object nameObj = model.getValueAt(i, 1);  // name
            Object qtyObj = model.getValueAt(i, 2);   // qty
            Object priceObj = model.getValueAt(i, 3); // price

            if (nameObj == null) continue;
            String name = nameObj.toString().trim();

            double qty = parseNumber(qtyObj, numberPattern);
            double price = parseNumber(priceObj, numberPattern);

            double value = switch (choice) {
                case "Preț" -> price;
                case "Valoare totală (Cantitate × Preț)" -> qty * price;
                default -> qty;
            };

            if (value > 0) {
                names.add(name);
                values.add(value);
            }
        }

        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Nu există date numerice valide pentru grafic!");
            return;
        }

        // === Creăm graficul ===
        CategoryChart chart = new CategoryChartBuilder()
                .width(800)
                .height(500)
                .title("📊 " + choice)
                .xAxisTitle("Produs")
                .yAxisTitle(choice)
                .build();

        chart.getStyler().setHasAnnotations(true);
        chart.getStyler().setLegendVisible(true);
        chart.addSeries(choice, names, values);

        // === Deschidem graficul într-un thread separat ===
        new Thread(() -> {
            JFrame frame = new SwingWrapper<>(chart).displayChart();
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // doar graficul se închide
            frame.setTitle("📈 Vizualizare stocuri");
        }).start();
    }

    // === Conversie sigură text → număr ===
    private double parseNumber(Object obj, Pattern pattern) {
        if (obj == null) return 0;
        String str = obj.toString().trim();
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            Matcher m = pattern.matcher(str);
            return m.find() ? Double.parseDouble(m.group(1)) : 0;
        }
    }
}
