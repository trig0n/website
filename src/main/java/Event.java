import java.util.HashMap;

class Event {
    private Integer id;
    private String name;
    private String description;
    private String html;

    Event() {
    }

    Event(Integer id, String name, String description, String html) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.html = html;
    }

    HashMap<String, Object> toHashMap() {
        HashMap<String, Object> hm = new HashMap<>();
        hm.put("name", name);
        hm.put("description", description);
        return hm;
    }

    Integer getId() {
        return id;
    }

    String getName() {
        return this.name;
    }

    String getDescription() {
        return this.description;
    }

    String getHtml() {
        return this.html;
    }

}