import java.util.List;

public class FileSystemStructure {
    private List<File> files;

    public FileSystemStructure() {
    }

    public FileSystemStructure(List<File> files) {
        this.files = files;
    }

    public List<File> getFiles() {
        return files;
    }

    public void setFiles(List<File> files) {
        this.files = files;
    }
}

class File {
    private String path;
    private String name;
    private String data;

    public File() {
    }

    public File(String path, String name, String data) {
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
