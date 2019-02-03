package app;

import org.junit.Test;

public class AppTest {

    @Test
    public void main() {
        try {
            String o = "http://www.qq.com/abc.jpg?abcmesmearmem";
            String n = o.substring(0, o.indexOf("?"));
            System.out.println(n);
        }
        catch (Exception e) {

        }
    }
}