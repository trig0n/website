import java.util.List;

public class FileSystemStructure {
    List<File> files;

    FileSystemStructure() {
    }

    FileSystemStructure(List<File> files) {
        this.files = files;
    }
}

class File {
    String path;
    String name;
    String data;

    File() {
    }

    File(String path, String name, String data) {
        this.path = path;
        this.name = name;
        this.data = data;
    }
}