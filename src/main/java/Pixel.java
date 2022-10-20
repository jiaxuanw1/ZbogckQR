import java.awt.image.BufferedImage;
import java.util.Objects;

public class Pixel {

    private final BufferedImage image;
    private final int x;
    private final int y;
    private final int rgb;

    public Pixel(BufferedImage image, int x, int y) {
        this.image = image;
        this.x = x;
        this.y = y;
        rgb = image.getRGB(x, y);
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getRGB() {
        return rgb;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Pixel)) {
            return false;
        }
        Pixel p = (Pixel) o;
        return x == p.getX() && y == p.getY() && image == p.getImage();
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, image);
    }

    @Override
    public String toString() {
        return "Pixel{" + "x=" + x + ", y=" + y + ", rgb=" + Integer.toHexString(rgb) + "}";
    }

}
