import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.File;
import javax.imageio.ImageIO;

/** Captures the X11 display using only the JDK, avoiding screenshot-tool packages. */
public final class ScreenCapture {
    private ScreenCapture() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: ScreenCapture <output.png>");
        }
        Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        var image = new Robot().createScreenCapture(bounds);
        File output = new File(args[0]);
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create screenshot directory: " + parent);
        }
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("No PNG ImageIO writer is available");
        }
        System.out.println("Captured " + bounds.width + "x" + bounds.height + " display to " + output);
    }
}
