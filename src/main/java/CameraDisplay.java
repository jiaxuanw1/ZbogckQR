import nu.pattern.OpenCV;
import org.bytedeco.javacv.CanvasFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CameraDisplay {

    private final Camera camera;
    private final CanvasFrame canvas;

    public static void main(String[] args) {
        new CameraDisplay();
    }

    public CameraDisplay() {
        OpenCV.loadLocally();

        camera = new Camera();

        // Initialize display window
        canvas = new CanvasFrame("Camera");
        canvas.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        canvas.setLayout(new FlowLayout());
        canvas.setPreferredSize(new Dimension(740, 600));

        // Set system theme
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Error setting system theme!");
        }

        // Take Picture button
        JButton scanQRButton = new JButton("Scan QR Code!");
        scanQRButton.addActionListener((e) -> {
            try {
                BufferedImage qrImage = camera.scanQR();
                displayQRReading(qrImage);
            } catch (QRNotFoundException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage());
            }
        });
        canvas.add(scanQRButton);

        // Take Picture button
        JButton shutterButton = new JButton("Take Picture!");
        shutterButton.addActionListener((e) -> camera.displayFrame());
        canvas.add(shutterButton);

        // Show QR Outline check box
        JCheckBox scanningQRCheckBox = new JCheckBox("Show QR Outline");
        scanningQRCheckBox.setSelected(true);
        scanningQRCheckBox.addActionListener((e) -> camera.setScanningQR(scanningQRCheckBox.isSelected()));
        canvas.add(scanningQRCheckBox);

        // Toggle BW/Color check box
        JCheckBox bwCheckBox = new JCheckBox("Black/White");
        bwCheckBox.setSelected(false);
        bwCheckBox.addActionListener((e) -> camera.setBw(bwCheckBox.isSelected()));
        canvas.add(bwCheckBox);

        // Mirror Image check box
        JCheckBox mirrorCheckBox = new JCheckBox("Mirrored");
        mirrorCheckBox.setSelected(false);
        mirrorCheckBox.addActionListener((e) -> camera.setMirrored(mirrorCheckBox.isSelected()));
        canvas.add(mirrorCheckBox);

        // Black/White Threshold slider
        JSlider bwSlider = new JSlider(0, 255, 150);
        bwSlider.setBorder(BorderFactory.createTitledBorder("Black/White Threshold: 150"));
        bwSlider.addChangeListener((e) -> {
            camera.setBwThreshold(bwSlider.getValue());
            bwSlider.setBorder(BorderFactory.createTitledBorder("Black/White Threshold: " + bwSlider.getValue()));
        });
        canvas.add(bwSlider);

        // Start camera capture
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            BufferedImage currentFrame = camera.getCurrentFrame();
            canvas.showImage(currentFrame);

//            try {
//                BufferedImage qrImage = camera.scanQR();
//                boolean[][] grid = QRUtil.qrToBooleanGrid(qrImage);
//                String text = QRUtil.decode(grid);
//                camera.displayImage(QRUtil.booleanGridToQR(grid));
//                JOptionPane.showMessageDialog(null, text);
//            } catch (Exception ex) {
//            }

        }, 0, 1000 / camera.getFPS(), TimeUnit.MILLISECONDS);

    }

    public void displayQRReading(BufferedImage qrImage) {
        // Initialize new frame
        JFrame window = new JFrame("QR Code");
        window.setPreferredSize(new Dimension(650, 395));
        window.getContentPane().setLayout(null);
        window.setVisible(true);

        // Decode QR code, get hidden text and bit.ly link
        boolean[][] grid = QRUtil.qrToBooleanGrid(qrImage);
        BufferedImage qrDisplayImage = QRUtil.booleanGridToQR(grid);
        String text;
        try {
            text = QRUtil.decode(grid);
        } catch (InvalidQRException ex) {
            text = null;
            System.out.println(ex.getMessage());
        }
        final String encryptedText = text;
        final String bitlyURL;
        if (encryptedText != null) {
            bitlyURL = "https://bit.ly/" + encryptedText;
        } else {
            bitlyURL = null;
        }

        // QR code display
        JPanel imagePanel = new JPanel();
        imagePanel.add(new JLabel(new ImageIcon(qrDisplayImage)));
        imagePanel.setBounds(10, 11, 330, 330);
        window.getContentPane().add(imagePanel);

        // Encrypted text label
        JLabel encryptedTextLabel = new JLabel("Encrypted text:  " + text);
        if (encryptedText != null) {
            encryptedTextLabel.setText("Encrypted text:  " + encryptedText);
        } else {
            encryptedTextLabel.setText("Invalid QR code!");
        }
        encryptedTextLabel.setFont(new Font("Consolas", Font.PLAIN, 14));
        encryptedTextLabel.setBounds(375, 143, 230, 15);
        window.getContentPane().add(encryptedTextLabel);

        // Open bit.ly link button
        JButton bitlyButton = new JButton("Open bit.ly link");
        bitlyButton.setBounds(375, 160, 220, 23);
        bitlyButton.addActionListener((e) -> {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(new URI(bitlyURL));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Error opening link!");
                }
            }
        });
        window.getContentPane().add(bitlyButton);

        // Copy text button
        JButton copyTextButton = new JButton("Copy text");
        copyTextButton.setBounds(375, 186, 105, 23);
        copyTextButton.addActionListener((e) -> copyToClipboard(encryptedText));
        window.getContentPane().add(copyTextButton);

        // Copy link button
        JButton copyLinkButton = new JButton("Copy bit.ly link");
        copyLinkButton.setBounds(490, 186, 105, 23);
        copyLinkButton.addActionListener((e) -> copyToClipboard(bitlyURL));
        window.getContentPane().add(copyLinkButton);

        // Disable buttons if text is null (i.e. QR code is invalid)
        if (encryptedText == null) {
            bitlyButton.setEnabled(false);
            copyTextButton.setEnabled(false);
            copyLinkButton.setEnabled(false);
        }

        window.pack();
    }

    public void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

}
