import java.util.List;

public class FileSystemStructure {
    private List<FileStructure> files;

    public FileSystemStructure() {
    }

    public FileSystemStructure(List<FileStructure> files) {
        this.files = files;
    }

    public List<FileStructure> getFiles() {
        return files;
    }

    public void setFiles(List<FileStructure> files) {
        this.files = files;
    }
}
