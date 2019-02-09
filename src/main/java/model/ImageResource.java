package model;

import com.evernote.edam.type.Data;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.ResourceAttributes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ImageResource extends Resource {
    ImageResource(ImageFile imageFile) throws NoSuchAlgorithmException {
        String mime = imageFile.getMIME();

        this.setMime(mime);
        this.setHeight((short) imageFile.getHeight());
        this.setWidth((short) imageFile.getWidth());
        this.setAttributes(new ResourceAttributes());

        byte[] content = imageFile.getContent();
        Data data = new Data();
        data.setBody(content);
        data.setSize(content.length);
        data.setBodyHash(MessageDigest.getInstance("MD5").digest(content));
        this.setData(data);
    }

    public ImageTag toImageTag() {
        return new ImageTag(this);
    }
}
