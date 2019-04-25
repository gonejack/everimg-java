package libs.dl;

public class Result {
    private String url;
    private String file;
    private boolean suc;
    private Exception exception;

    Result(String url, String file, boolean suc, Exception exception) {
        this.url = url;
        this.file = file;
        this.suc = suc;
        this.exception = exception;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isSuc() {
        return suc;
    }

    public void setSuc(boolean suc) {
        this.suc = suc;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}