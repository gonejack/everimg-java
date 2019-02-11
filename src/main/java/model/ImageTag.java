package model;


import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

public class ImageTag {
    ImageResource resource;

    ImageTag(ImageResource resource) {
        this.resource = resource;
    }

    public String genTag(Element imageNode) {
        String tagTpl = "<en-media %s />";

        List<String> attrs = new ArrayList<>();

        attrs.add(String.format("type=\"%s\"", resource.getMime()));
        attrs.add(String.format("hash=\"%s\"", bytesToHex(resource.getData().getBodyHash())));

        if (resource.getWidth() > 650) {
            attrs.add(String.format("width=\"%s\"", 650));
        }

        if (!imageNode.attr("style").isEmpty()) {
            attrs.add(String.format("style=\"%s\"", imageNode.attr("style")));
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
