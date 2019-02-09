package model;

import java.util.ArrayList;
import java.util.List;

public class ImageTag {
    ImageResource resource;

    ImageTag(ImageResource resource) {
        this.resource = resource;
    }

    @Override
    public String toString() {
        String tagTpl = "<en-media %s />";

        List<String> attrs = new ArrayList<>();

        attrs.add(String.format("type=\"%s\"", resource.getMime()));
        attrs.add(String.format("hash=\"%s\"", bytesToHex(resource.getData().getBodyHash())));

        if (resource.getWidth() > 650) {
            attrs.add(String.format("width=\"%s\"", 650));
        } else {
            attrs.add(String.format("height=\"%s\"", resource.getHeight()));
            attrs.add(String.format("width=\"%s\"", resource.getWidth()));
        }

        return String.format(tagTpl, String.join(" ", attrs));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte hashByte : bytes) {
            int intVal = 0xff & hashByte;
            if (intVal < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(intVal));
        }
        return sb.toString();
    }

}
