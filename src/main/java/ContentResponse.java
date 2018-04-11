public class ContentResponse {
    private String name;
    private String data;

    public ContentResponse() {
    }

    ContentResponse(String name, String data) {
        this.name = name;
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
