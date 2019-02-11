package model;


import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageTag {
    ImageResource resource;

    ImageTag(ImageResource resource) {
        this.resource = resource;
    }

    public String genTag(Element sourceNode) {
        String tagTpl = "<en-media %s />";

        List<String> attrs = new ArrayList<>();

        attrs.add(String.format("type=\"%s\"", resource.getMime()));
        attrs.add(String.format("hash=\"%s\"", bytesToHex(resource.getData().getBodyHash())));

        if (resource.getWidth() > 650) {
            attrs.add(String.format("width=\"%s\"", 650));
        }

        for (String attr : Arrays.asList("style", "title", "lang", "dir", "align", "alt", "longdesc", "border", "hspace", "vspace", "usemap")) {
            String attrv = sourceNode.attr(attr);

            if (!attrv.isBlank()) {
                attrs.add(String.format("%s=\"%s\"", attr, attrv));
            }
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
