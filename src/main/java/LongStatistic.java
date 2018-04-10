public class LongStatistic {
    private String name;
    private Long value;

    public LongStatistic(String name) {
        this.name = name;
        value = new Long("0");
    }

    public LongStatistic(String name, Long value) {
        this.name = name;
        this.value = value;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}