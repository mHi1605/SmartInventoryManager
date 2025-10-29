package org.example;

import com.formdev.flatlaf.themes.FlatMacLightLaf;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // === Tema inițială ===
        FlatMacLightLaf.setup();

        SwingUtilities.invokeLater(InventoryGUI::new);
    }
}
