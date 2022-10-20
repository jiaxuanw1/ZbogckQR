import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class QRGenerator {

    private final JFrame frame;
    private BufferedImage qrImage;
    private String qrText;

    public QRGenerator() {
        // Initialize frame
        frame = new JFrame("QR Code Generator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(650, 395));
        frame.getContentPane().setLayout(null);
        frame.setVisible(true);

        // Set system theme
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Error setting system theme!");
        }

        // Display default QR image (or blank white image if unable to load)
        String defaultImgName = "QR_Goes_Here.png";
        try {
            qrImage = ImageIO.read(new File("src/imgrecognition/images/" + defaultImgName));
        } catch (IOException e) {
            System.out.println("LOAD IMAGE FAILED!! " + defaultImgName);
            qrImage = new BufferedImage(QRUtil.IMAGE_SIZE, QRUtil.IMAGE_SIZE, BufferedImage.TYPE_BYTE_BINARY);
            Graphics2D g2d = qrImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, QRUtil.IMAGE_SIZE, QRUtil.IMAGE_SIZE);
            g2d.dispose();
        }

        // QR code display
        JPanel imagePanel = new JPanel();
        imagePanel.add(new JLabel(new ImageIcon(qrImage)));
        imagePanel.setBounds(10, 11, 330, 330);
        frame.getContentPane().add(imagePanel);

        // Text input instruction label
        JLabel inputLabel = new JLabel("Text to encrypt:");
        inputLabel.setBounds(350, 143, 84, 14);
        frame.getContentPane().add(inputLabel);

        // Text input field
        JTextField inputField = new JTextField();
        inputField.setLocation(350, 162);
        inputField.setSize(132, 23);
        inputField.setColumns(15);
        frame.getContentPane().add(inputField);

        // Generate QR code button
        JButton generateButton = new JButton("Generate QR code");
        generateButton.setBounds(492, 162, 125, 23);
        generateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                qrText = inputField.getText();
                int bitlyIndex = qrText.indexOf("bit.ly/");
                if (bitlyIndex >= 0) {
                    qrText = qrText.substring(bitlyIndex + 7);
                }

                boolean[][] qrGrid = QRUtil.encode(qrText);
                qrImage = QRUtil.booleanGridToQR(qrGrid);

                imagePanel.removeAll();
                imagePanel.add(new JLabel(new ImageIcon(qrImage)));

                frame.repaint();
                frame.revalidate();
            }
        });
        frame.getContentPane().add(generateButton);

        // Save QR code button
        JButton saveButton = new JButton("Save as PNG");
        saveButton.setBounds(492, 196, 125, 23);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (qrText == null) {
                    JOptionPane.showMessageDialog(null, "No QR code generated yet!");
                } else {
                    String fileName = JOptionPane.showInputDialog(null,
                            "Please enter a file name for the QR code image:\n(file extension not needed)");
                    if (fileName == null) {
                        return;
                    }
                    if (fileName.length() == 0) {
                        fileName = qrText;
                    }

                    try {
                        ImageIO.write(qrImage, "PNG", new File("src/imgrecognition/images/" + fileName + ".png"));
                    } catch (IOException ex) {
                        System.out.println("WRITE IMAGE FAILED!! " + fileName);
                        ex.printStackTrace();
                    }
                }
            }
        });
        frame.getContentPane().add(saveButton);

        frame.pack();

    }

    public static void main(String[] args) {
        new QRGenerator();
    }
}
