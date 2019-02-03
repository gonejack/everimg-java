package libs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImageFile {
    private byte[] content;
    private BufferedImage img;

    public ImageFile(String file) throws Exception {
        content = Files.readAllBytes(Path.of(file));

        img = ImageIO.read(new ByteArrayInputStream(content));
        if (img == null) {
            throw new Exception(String.format("%s is not valid image file", file));
        }
    }

    public byte[] getContent() {
        return content;
    }
    public String getMIME() throws IOException {
        return URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(content));
    }
    public int getHeight() {
        return img.getHeight();
    }
    public int getWidth() {
        return img.getWidth();
    }
}
