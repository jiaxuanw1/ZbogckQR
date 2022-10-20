import java.awt.*;
import java.awt.image.BufferedImage;

public class QRUtil {

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz% ";

    public static final int SQUARE_SIZE = 40;
    public static final int BLACK_BORDER = 15;
    public static final int WHITE_BORDER = 10;
    public static final int BORDER_SIZE = BLACK_BORDER + WHITE_BORDER; // 25
    public static final int IMAGE_SIZE = SQUARE_SIZE * 7 + BORDER_SIZE * 2; // 330

    public static String readAndDecode(BufferedImage bimg) throws InvalidQRException {
        return decode(qrToBooleanGrid(bimg));
    }

    public static BufferedImage booleanGridToQR(boolean[][] grid) {
        BufferedImage qrImage = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_BYTE_BINARY);

        for (int x = 0; x < IMAGE_SIZE; x++) {
            for (int y = 0; y < IMAGE_SIZE; y++) {
                // Black outer border
                if (x < BLACK_BORDER || x >= IMAGE_SIZE - BLACK_BORDER || y < BLACK_BORDER || y >= IMAGE_SIZE - BLACK_BORDER) {
                    qrImage.setRGB(x, y, 0xFF000000);
                }
                // White inner border
                else if (x < BORDER_SIZE || x >= IMAGE_SIZE - BORDER_SIZE || y < BORDER_SIZE || y >= IMAGE_SIZE - BORDER_SIZE) {
                    qrImage.setRGB(x, y, 0xFFFFFFFF);
                }
                // Inside QR code
                else {
                    int r = (y - BORDER_SIZE - 1) / SQUARE_SIZE;
                    int c = (x - BORDER_SIZE - 1) / SQUARE_SIZE;
                    qrImage.setRGB(x, y, grid[r][c] ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
        }

        return qrImage;
    }

    public static boolean[][] qrToBooleanGrid(BufferedImage bimg) {
        boolean[][] grid = new boolean[7][7];

        // Scale image to 600 by 600 pixels
        Image img = bimg.getScaledInstance(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.SCALE_SMOOTH);
        BufferedImage scaledImg = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImg.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();

        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                int x = c * (IMAGE_SIZE - BORDER_SIZE * 2) / 7 + ((IMAGE_SIZE - BORDER_SIZE * 2) / 14 + BORDER_SIZE);
                int y = r * (IMAGE_SIZE - BORDER_SIZE * 2) / 7 + ((IMAGE_SIZE - BORDER_SIZE * 2) / 14 + BORDER_SIZE);
                grid[r][c] = scaledImg.getRGB(x, y) == 0xFF000000;
                scaledImg.setRGB(x, y, 0xFF00FFFF); // add dots... get rid of once finalized
            }
        }

//        JFrame window = new JFrame();
//        window.add(new JLabel(new ImageIcon(scaledImg)));
//        window.pack();
//        window.setVisible(true);
        return grid;
    }

    public static boolean[][] encode(String text) {
        // Append ignore characters if less than 7 characters
        if (text.length() < 7) {
            text += "%".repeat(7 - text.length());
        }

        boolean[][] grid = new boolean[7][7];

        for (int i = 0; i < 7; i++) {
            // Get 6-bit binary representation of the character's number mapping.
            // If the character does not have a mapping, encode an ignore character.
            int charNum = charToNum(text.charAt(i));
            if (charNum < 0) {
                charNum = charToNum('%');
            }
            String bin = toFixedSizeBinaryString(charNum, 6);

            // For columns 1 and 5, skip the locations of the orientation bits and write the
            // last bit in the corresponding adjacent column
            if (i == 1 || i == 5) {
                for (int bitPos = 0; bitPos < bin.length(); bitPos++) {
                    if (bitPos == 0) {
                        grid[0][i] = bin.charAt(bitPos) == '1';
                    } else if (bitPos <= 3) {
                        grid[bitPos + 1][i] = bin.charAt(bitPos) == '1';
                    } else if (bitPos == 4) {
                        grid[6][i] = bin.charAt(bitPos) == '1';
                    } else {
                        grid[6][i + (i == 1 ? -1 : 1)] = bin.charAt(bitPos) == '1';
                    }
                }
            }
            // For the other columns, write the bits in the first 6 positions in the column
            else {
                for (int bitPos = 0; bitPos < 6; bitPos++) {
                    grid[bitPos][i] = bin.charAt(bitPos) == '1';
                }
            }
        }

        // Set orientation bits
        grid[1][1] = false;
        grid[1][5] = true;
        grid[5][1] = true;
        grid[5][5] = true;

        // Generate and store checksum (number of ON bits modulus 8)
        int bitsOn = 0;
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c]) {
                    bitsOn++;
                }
            }
        }
        String checksum = toFixedSizeBinaryString(bitsOn % 8, 3);
        for (int bitPos = 0; bitPos < 3; bitPos++) {
            grid[6][bitPos + 2] = checksum.charAt(bitPos) == '1';
        }

        return grid;
    }

    public static String decode(boolean[][] grid) throws InvalidQRException {
        // Adjust orientation
        grid = orient(grid);

        // Check if the checksum matches the reading
        int bitsOn = 0;
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if ((c < 2 || c > 4 || r != 6) && grid[r][c]) {
                    bitsOn++;
                }
            }
        }
        StringBuilder checksum = new StringBuilder();
        for (int c = 2; c <= 4; c++) {
            checksum.append(grid[6][c] ? 1 : 0);
        }
        if (Integer.parseInt(checksum.toString(), 2) != bitsOn % 8) {
            throw new InvalidQRException("Checksum does not match.");
        }

        StringBuilder text = new StringBuilder();

        for (int c = 0; c < 7; c++) {
            StringBuilder binary = new StringBuilder();

            // For columns 1 and 5, skip the orientation bits and read the last bit in the
            // corresponding adjacent column
            if (c == 1 || c == 5) {
                for (int r = 0; r < 7; r++) {
                    if (r != 1 && r != 5) {
                        binary.append(grid[r][c] ? 1 : 0);
                    }
                }
                binary.append(grid[6][c + (c == 1 ? -1 : 1)] ? 1 : 0);
            }
            // For the other columns, read the first six bits in the column
            else {
                for (int r = 0; r < 6; r++) {
                    binary.append(grid[r][c] ? 1 : 0);
                }
            }

            char ch = numToChar(Integer.parseInt(binary.toString(), 2));
            if (ch != '%') {
                text.append(ch);
            }
        }

        return text.toString();
    }

    public static char numToChar(int i) {
        return CHARS.charAt(i);
    }

    public static int charToNum(char c) {
        return CHARS.indexOf(c);
    }

    /**
     * Returns the specified boolean grid reading oriented such that the top-left
     * orientation bit is off.
     */
    public static boolean[][] orient(boolean[][] grid) throws InvalidQRException {
        int[] rotations = { 0, 90, 270, 180 };
        boolean[] orientationBits = { grid[1][1], grid[1][5], grid[5][1], grid[5][5] };

        boolean[][] orientedGrid = null;
        int bitsOff = 0;
        for (int i = 0; i < orientationBits.length; i++) {
            if (!orientationBits[i]) {
                orientedGrid = rotate(grid, rotations[i]);
                bitsOff++;
            }
        }

        if (bitsOff != 1) {
            throw new InvalidQRException("Number of orientation bits off: " + bitsOff);
        }

        return orientedGrid;
    }

    /**
     * Returns the specified boolean 2D array with the specified counterclockwise
     * rotation applied.
     */
    public static boolean[][] rotate(boolean[][] grid, int degrees) {
        final int M = grid.length;
        final int N = grid[0].length;

        boolean[][] rotated;
        int ccwRotations = degrees / 90;
        switch (ccwRotations % 4) {
            // Rotate 90 degrees counterclockwise
            case 1:
                rotated = new boolean[N][M];
                for (int r = 0; r < M; r++) {
                    for (int c = 0; c < N; c++) {
                        rotated[N - 1 - c][r] = grid[r][c];
                    }
                }
                break;
            // Rotate 180 degrees
            case 2:
                rotated = new boolean[M][N];
                for (int r = 0; r < M; r++) {
                    for (int c = 0; c < N; c++) {
                        rotated[M - 1 - r][N - 1 - c] = grid[r][c];
                    }
                }
                break;
            // Rotate 90 degrees clockwise
            case 3:
                rotated = new boolean[N][M];
                for (int r = 0; r < M; r++) {
                    for (int c = 0; c < N; c++) {
                        rotated[c][M - 1 - r] = grid[r][c];
                    }
                }
                break;
            // No rotation
            default:
                return grid;
        }

        return rotated;
    }

    /**
     * Returns a string representation of the given number in binary with the
     * appropriate leading 0's to make it the specified length.
     */
    public static String toFixedSizeBinaryString(int i, int length) {
        String bin = Integer.toBinaryString(i);
        if (bin.length() > length) {
            throw new IllegalArgumentException("Binary number must fit within specified length!");
        }
        return "0".repeat(length - bin.length()) + bin;
    }

    /**
     * Prints a 2D boolean array, with 1 representing true and 0 representing false.
     */
    public static void printGrid(boolean[][] grid) {
        StringBuilder output = new StringBuilder();
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                output.append(grid[r][c] ? "1 " : "0 ");
                if (c == grid.length - 1) {
                    output.append("\n");
                }
            }
        }
        System.out.println(output);
    }

}
