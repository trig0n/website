
public class FileStructure {
    private String path;
    private String name;
    private String data;

    public FileStructure() {
    }

    public FileStructure(String path, String name, String data) {
        this.path = path;
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}