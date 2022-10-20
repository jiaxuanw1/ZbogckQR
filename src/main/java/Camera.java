import nu.pattern.OpenCV;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Camera {

    private final String imageDirectory = "src/main/resources/";

    private final int FPS;

    private final FrameGrabber grabber;
    private final Java2DFrameConverter converter;
    private BufferedImage currentFrame;

    private boolean scanningQR = true;
    private boolean bwOn = false;
    private boolean mirrored = false;
    private int bwThreshold = 150;

    private MatOfPoint2f qrContour;

    /**
     * Starts the frame grabber.
     */
    public Camera() {
        OpenCV.loadLocally();

        grabber = new OpenCVFrameGrabber(0);
        converter = new Java2DFrameConverter();

        try {
            grabber.start();
            currentFrame = converter.convert(grabber.grab());
        } catch (Exception e) {
            System.out.println("Could not start camera!");
        }

        FPS = (int) grabber.getFrameRate();
    }

    /**
     * Updates and returns the current frame with the selected settings applied.
     */
    public BufferedImage getCurrentFrame(boolean scanningQR, boolean mirrored, boolean bwOn) {
        try {
            currentFrame = converter.convert(grabber.grab());
        } catch (Exception e) {
            System.out.println("Error capturing frame!");
        }

        if (scanningQR) {
            currentFrame = findQRCode(currentFrame);
        }
        if (mirrored) {
            currentFrame = mirror(currentFrame);
        }
        if (bwOn) {
            currentFrame = blackAndWhite(currentFrame, bwThreshold);
        }

        return currentFrame;
    }

    public BufferedImage getCurrentFrame() {
        return getCurrentFrame(scanningQR, mirrored, bwOn);
    }

    /**
     * Displays the specified image in a new window.
     */
    public void displayImage(BufferedImage bimg) {
        if (bimg != null) {
            JFrame window = new JFrame("Image Display");
            window.add(new JLabel(new ImageIcon(bimg)));
            window.pack();
            window.setVisible(true);
        }
    }

    /**
     * Displays the current frame in a new window.
     */
    public void displayFrame() {
        displayImage(getCurrentFrame());
    }

    public void saveImage(BufferedImage b, String fileName) {
        try {
            ImageIO.write(b, "PNG", new File(imageDirectory + fileName));
        } catch (Exception e) {
            System.out.println("WRITE IMAGE FAILED!! " + fileName);
            e.printStackTrace();
        }
    }

    /**
     * Scans the current frame for a QR code. If found, the method fits the QR code
     * portion of the image to a square image, which is returned.
     */
    public BufferedImage scanQR() throws QRNotFoundException {
        BufferedImage frame = getCurrentFrame(false, false, false);
        Mat origImg = bufferedImage2Mat(frame);
        findQRCode(frame);

        // Calculate center of mass of contour image using moments
        Moments moment = Imgproc.moments(qrContour);
        int x = (int) (moment.get_m10() / moment.get_m00());
        int y = (int) (moment.get_m01() / moment.get_m00());

        // Sort points relative to center of mass
        Point[] sortedPoints = new Point[4];

        double[] data;
        for (int i = 0; i < qrContour.rows(); i++) {
            data = qrContour.get(i, 0);
            double datax = data[0];
            double datay = data[1];
            if (datax < x && datay < y) {
                sortedPoints[0] = new Point(datax, datay);
            } else if (datax > x && datay < y) {
                sortedPoints[1] = new Point(datax, datay);
            } else if (datax < x && datay > y) {
                sortedPoints[2] = new Point(datax, datay);
            } else if (datax > x && datay > y) {
                sortedPoints[3] = new Point(datax, datay);
            }
        }

        // Transform to image with the size specified by QRUtil
        try {
            int imgSize = QRUtil.IMAGE_SIZE;
            int bwThresholdQR = 180;

            MatOfPoint2f src = new MatOfPoint2f(sortedPoints[0], sortedPoints[1], sortedPoints[2], sortedPoints[3]);
            MatOfPoint2f dst = new MatOfPoint2f(
                    new Point(0, 0),
                    new Point(imgSize - 1, 0),
                    new Point(0, imgSize - 1),
                    new Point(imgSize - 1, imgSize - 1));

            Mat warpMat = Imgproc.getPerspectiveTransform(src, dst);

            Mat destImg = new Mat();
            Imgproc.warpPerspective(origImg, destImg, warpMat, new Size(imgSize, imgSize));

            BufferedImage qrImg = mat2BufferedImage(destImg);
            return blackAndWhite(qrImg, bwThresholdQR);
        } catch (Exception e) {
            throw new QRNotFoundException();
        }

    }

    /**
     * Finds the QR code in the specified image and assigns the contour of the QR
     * code's outline to qrContour. Returns the image with the outline drawn.
     */
    public BufferedImage findQRCode(BufferedImage bimg) {
        Mat src = bufferedImage2Mat(bimg);

        // Convert image to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
        // Blur image to smooth out noise
        Mat blur = new Mat();
        Imgproc.blur(gray, blur, new Size(3, 3));

        // Detecting edges
        Mat edges = new Mat();
        double threshold1 = 60;
        Imgproc.Canny(blur, edges, threshold1, threshold1 * 3);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        MatOfPoint2f largestRectContour = new MatOfPoint2f();
        double largestArea = 0;

        // Gets the rectangular contour with the largest enclosed area
        for (MatOfPoint contour : contours) {
            // Finds the area enclosed by the contour
            double contourArea = Imgproc.contourArea(contour);

            // Approximates the contour as a polygon
            MatOfPoint2f contour2f = new MatOfPoint2f();
            MatOfPoint2f approxPolygon = new MatOfPoint2f();
//            contour2f.fromList(contour.toList());
            contour.convertTo(contour2f, CvType.CV_32FC2);
            double epsilon = Imgproc.arcLength(contour2f, true) * 0.04;
            Imgproc.approxPolyDP(contour2f, approxPolygon, epsilon, true);

            // If the contour has 4 edges and a larger enclosed area, make it the new
            // largest rectangular contour
            long total = approxPolygon.total();
            if (total == 4 && contourArea > largestArea) {
                largestRectContour = approxPolygon;
                largestArea = contourArea;
            }
        }

        // Set QR contour to the largest rectangular contour found
        this.qrContour = largestRectContour;

        MatOfPoint approx1f = new MatOfPoint();
        largestRectContour.convertTo(approx1f, CvType.CV_32S);
        List<MatOfPoint> approximation = new ArrayList<MatOfPoint>();
        approximation.add(approx1f);

        // Draw outline onto frame
        if (approximation.get(0).isContinuous()) {
            Mat withContour = new Mat();
            src.copyTo(withContour);
            Imgproc.drawContours(withContour, approximation, 0, new Scalar(0, 255, 0), 3);
            BufferedImage imageWithOutline = mat2BufferedImage(withContour);
            return imageWithOutline;
        } else {
            return mat2BufferedImage(src);
        }
    }

    public Mat bufferedImage2Mat(BufferedImage bimg) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(bimg, "png", byteArrayOutputStream);
            byteArrayOutputStream.flush();
            return Imgcodecs.imdecode(new MatOfByte(byteArrayOutputStream.toByteArray()), Imgcodecs.IMREAD_UNCHANGED);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public BufferedImage mat2BufferedImage(Mat mat) {
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, matOfByte);
        try {
            return ImageIO.read(new ByteArrayInputStream(matOfByte.toArray()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns a new BufferedImage with the original image mirrored horizontally.
     */
    public BufferedImage mirror(BufferedImage bimg) {
        AffineTransform at = AffineTransform.getScaleInstance(-1, 1);
        at.concatenate(AffineTransform.getTranslateInstance(-bimg.getWidth(), 0));

        BufferedImage mirrored = new BufferedImage(bimg.getWidth(), bimg.getHeight(), bimg.getType());
        Graphics2D g2d = mirrored.createGraphics();
        g2d.transform(at);
        g2d.drawImage(bimg, 0, 0, null);
        g2d.dispose();
        return mirrored;
    }

    public BufferedImage grayScale(BufferedImage bimg) {
        BufferedImage gray = new BufferedImage(bimg.getWidth(), bimg.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gray.createGraphics();
        g2d.drawImage(bimg, 0, 0, null);
        g2d.dispose();
        return gray;
    }

    public BufferedImage blackAndWhite(BufferedImage bimg, int threshold) {
        final int w = bimg.getWidth();
        final int h = bimg.getHeight();

        BufferedImage gray = grayScale(bimg);
        BufferedImage bw = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if ((gray.getRGB(x, y) & 0xFF) > threshold) {
                    bw.setRGB(x, y, 0xFFFFFFFF);
                } else {
                    bw.setRGB(x, y, 0xFF000000);
                }
            }
        }
        return bw;
    }

    /**
     * The value of the output pixel is the maximum value of all pixels in the
     * neighborhood. In a binary image, a pixel is set to 1 if any of the
     * neighboring pixels have the value 1.
     * <p>
     * Morphological dilation makes objects more visible and fills in small holes in
     * objects.
     */
    public BufferedImage dilate(BufferedImage bimg) {
        final int w = bimg.getWidth();
        final int h = bimg.getHeight();
        BufferedImage dilated = new BufferedImage(w, h, bimg.getType());

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int maxRGB = bimg.getRGB(x, y);
                List<Pixel> neighbors = getNeighboringPixels(x, y, bimg);
                for (Pixel p : neighbors) {
                    maxRGB = Math.max(p.getRGB(), maxRGB);
                }
                dilated.setRGB(x, y, maxRGB);
            }
        }

        return dilated;
    }

    /**
     * The value of the output pixel is the minimum value of all pixels in the
     * neighborhood. In a binary image, a pixel is set to 0 if any of the
     * neighboring pixels have the value 0.
     * <p>
     * Morphological erosion removes islands and small objects so that only
     * substantive objects remain.
     */
    public BufferedImage erode(BufferedImage bimg) {
        final int w = bimg.getWidth();
        final int h = bimg.getHeight();
        BufferedImage eroded = new BufferedImage(w, h, bimg.getType());

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int minRGB = bimg.getRGB(x, y);
                List<Pixel> neighbors = getNeighboringPixels(x, y, bimg);
                for (Pixel p : neighbors) {
                    minRGB = Math.min(p.getRGB(), minRGB);
                }
                eroded.setRGB(x, y, minRGB);
            }
        }

        return eroded;
    }

    /**
     * Returns an ArrayList of points with the coordinates of all the neighbors of
     * the given pixel in the specified image. Note that this does not include the
     * original pixel itself.
     */
    public List<Pixel> getNeighboringPixels(int x, int y, BufferedImage bimg) {
        List<Pixel> neighbors = new ArrayList<Pixel>();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (i >= 0 && i < bimg.getWidth() && j >= 0 && j < bimg.getHeight() && !(i == x && j == y)) {
                    neighbors.add(new Pixel(bimg, i, j));
                }
            }
        }
        return neighbors;
    }

    /**
     * Returns a copy of the specified BufferedImage with type ARGB.
     */
    public BufferedImage copyRGBImage(BufferedImage bimg) {
        BufferedImage copy = new BufferedImage(bimg.getWidth(), bimg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(bimg, 0, 0, null);
        g2d.dispose();
        return copy;
    }

    public int getFPS() {
        return FPS;
    }

    public void setScanningQR(boolean scanningQR) {
        this.scanningQR = scanningQR;
    }

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }

    public void setBw(boolean bwOn) {
        this.bwOn = bwOn;
    }

    public void setBwThreshold(int bwThreshold) {
        this.bwThreshold = bwThreshold;
    }

}
