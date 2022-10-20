import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Error setting system theme!");
        }

        JFrame frame = new JFrame("ZbogckQR");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(250, 100));
        frame.setLocation(600, 300);

        JPanel panel = new JPanel();
        frame.add(panel);

        String[] options = {"Camera / QR Reader", "QR Code Generator"};
        JComboBox<String> dropdown = new JComboBox<>(options);
        dropdown.setPreferredSize(new Dimension(200, 30));
        panel.add(dropdown);

        JButton launchButton = new JButton("Launch");
        launchButton.addActionListener(e -> {
            switch (dropdown.getSelectedIndex()) {
                case 0 -> new CameraDisplay();
                case 1 -> new QRGenerator();
            }
        });
        panel.add(launchButton);

        frame.pack();
        frame.setVisible(true);
    }

}
