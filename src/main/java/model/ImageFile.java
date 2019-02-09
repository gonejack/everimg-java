package model;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImageFile {
    private final byte[] content;
    private final String mime;
    private final BufferedImage image;

    public ImageFile(String file) throws Exception {
        content = Files.readAllBytes(Path.of(file));

        image = ImageIO.read(new ByteArrayInputStream(content));
        if (image == null) {
            throw new Exception(String.format("%s is not valid file", file));
        }

        mime = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(content));
        if (mime == null) {
            throw new Exception(String.format("%s is not valid image file", file));
        }
    }

    public String getMIME() {
        return mime;
    }
    public byte[] getContent() {
        return content;
    }
    public int getHeight() {
        return image.getHeight();
    }
    public int getWidth() {
        return image.getWidth();
    }

    public ImageResource toImageResource() throws Exception {
        return new ImageResource(this);
    }
}
